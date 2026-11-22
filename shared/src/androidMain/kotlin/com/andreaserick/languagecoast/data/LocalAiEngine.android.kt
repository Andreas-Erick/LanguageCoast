package com.andreaserick.languagecoast.data

import android.app.ActivityManager
import android.app.DownloadManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.StatFs
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * LiteRT-LM ships its native library for 64-bit processors only. On a 32-bit phone, or where the app runs as
 * a 32-bit process, there is no library to load, so Local AI is not offered at all.
 */
actual val localAiAvailable: Boolean = android.os.Process.is64Bit()

/** Holds paths initialized by [AndroidLocalModelController] so [LocalAiTranslator] can locate downloaded models. */
internal object LocalAiPaths {
    @Volatile
    var modelsDir: File? = null

    @Volatile
    var filesDir: File? = null

    @Volatile
    var cacheDir: File? = null

    @Volatile
    var gpuSupported: Boolean = false

    @Volatile
    var onModelDeleted: ((String) -> Unit)? = null

    fun resolveModelFile(modelId: String, explicitPath: String?): File? {
        if (!explicitPath.isNullOrBlank()) {
            val file = File(explicitPath)
            if (file.exists() && file.length() > 0L) return file
        }
        val spec = LocalAiCatalog.byId(modelId) ?: LocalAiCatalog.MODELS.first()
        val candidates = listOfNotNull(
            modelsDir?.resolve(spec.fileName),
            filesDir?.resolve("local_models")?.resolve(spec.fileName),
            filesDir?.resolve(spec.fileName),
            filesDir?.resolve("model.litertlm")
        )
        return candidates.firstOrNull { it.exists() && it.length() > 0L }
    }
}

/**
 * Manages local models on Android: scans the hardware, and downloads `.litertlm` files with the
 * system's DownloadManager, which keeps going when the app is in the background, survives the process
 * being killed, resumes after a dropped connection and shows its own progress notification.
 * Models live in the app's external files directory (app-private, no permission needed), which is the
 * only place DownloadManager can write to.
 */
class AndroidLocalModelController(private val context: Context) : LocalModelController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloads = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val modelsFolder: File by lazy { (context.getExternalFilesDir(MODELS_DIR) ?: File(context.filesDir, MODELS_DIR)).apply { mkdirs() } }
    /** The DownloadManager id of each running download, by model id, so a restart can pick them up again. */
    private val pending by lazy { context.getSharedPreferences("local_model_downloads", Context.MODE_PRIVATE) }
    private val trackers = ConcurrentHashMap<String, Job>()

    private val _hardwareProfile = MutableStateFlow<DeviceHardwareProfile?>(null)
    override val hardwareProfile: StateFlow<DeviceHardwareProfile?> = _hardwareProfile.asStateFlow()

    override fun isOnMeteredNetwork(): Boolean =
        (context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)?.isActiveNetworkMetered == true

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _installStates = MutableStateFlow<Map<String, ModelInstallStatus>>(emptyMap())
    override val installStates: StateFlow<Map<String, ModelInstallStatus>> = _installStates.asStateFlow()

    init {
        LocalAiPaths.filesDir = context.filesDir
        LocalAiPaths.cacheDir = context.cacheDir
        scope.launch {
            LocalAiPaths.modelsDir = modelsFolder
            refreshInstalledModels()
            // Downloads started before the process was killed are still running in DownloadManager.
            pending.all.forEach { (modelId, id) -> if (id is Long) track(modelId, id) }
            scanHardware()
        }
    }

    override fun scanHardware() {
        if (_isScanning.value) return
        scope.launch {
            _isScanning.value = true
            try {
                val profile = readDeviceHardware(context)
                LocalAiPaths.gpuSupported = profile.gpuAccelerationSupported
                _hardwareProfile.value = profile
            } finally {
                _isScanning.value = false
            }
        }
    }

    override fun installModel(modelId: String, accessToken: String?, allowMetered: Boolean) {
        val model = LocalAiCatalog.byId(modelId) ?: return
        if (trackers[modelId]?.isActive == true) return
        if (model.gated && accessToken.isNullOrBlank()) {
            _installStates.update { it + (modelId to ModelInstallStatus.Failed(NEEDS_TOKEN)) }
            return
        }
        _installStates.update { it + (modelId to ModelInstallStatus.Downloading(progress = 0f, downloadedMb = 0L, totalMb = model.sizeMb)) }
        scope.launch {
            if (context.getExternalFilesDir(MODELS_DIR) == null) {
                _installStates.update { it + (modelId to ModelInstallStatus.Failed("This phone has no storage the download manager can write to.")) }
                return@launch
            }
            // DownloadManager refuses to overwrite, so clear leftovers from an earlier attempt.
            partFile(model).delete()
            File(modelsFolder, model.fileName).delete()

            val request = DownloadManager.Request(Uri.parse(model.downloadUrl))
                .setTitle("Language Coast · ${model.name}")
                .setDescription("Local AI model, ${model.sizeLabel}")
                // Under another name until it is complete (see [partFile]).
                .setDestinationInExternalFilesDir(context, MODELS_DIR, model.fileName + PART_SUFFIX)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                // Without the user's word a download of this size only runs on Wi-Fi: DownloadManager waits while
                // there is none and carries on when it is back.
                .setAllowedOverMetered(allowMetered)
                .setAllowedOverRoaming(false)
            if (model.gated) request.addRequestHeader("Authorization", "Bearer ${accessToken!!.trim()}")

            val id = try {
                downloads.enqueue(request)
            } catch (e: Exception) {
                logError(TAG, "DownloadManager refused enqueue for ${model.fileName}", e)
                _installStates.update { it + (modelId to ModelInstallStatus.Failed("Couldn't start the download. Check that Downloads is enabled and try again.")) }
                return@launch
            }
            pending.edit().putLong(modelId, id).apply()
            track(modelId, id)
        }
    }

    /** Follows download [id] in DownloadManager until it ends, mirroring its progress into [installStates]. */
    private fun track(modelId: String, id: Long) {
        val model = LocalAiCatalog.byId(modelId) ?: return
        trackers[modelId]?.cancel()
        trackers[modelId] = scope.launch {
            var failedQueries = 0
            while (isActive) {
                // A cursor without a row means the download is gone; no cursor, or an exception, means
                // DownloadManager couldn't answer this time. That keeps the download: it is asked again.
                var answered = true
                val snapshot = try {
                    val cursor = downloads.query(DownloadManager.Query().setFilterById(id))
                    if (cursor == null) answered = false
                    cursor?.use { rows ->
                        if (!rows.moveToFirst()) return@use null
                        DownloadSnapshot(
                            status = rows.getInt(rows.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                            reason = rows.getInt(rows.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
                            downloaded = rows.getLong(rows.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                            total = rows.getLong(rows.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        )
                    }
                } catch (e: Exception) {
                    // Logged once per run of failures, so a DownloadManager that is down doesn't fill the log.
                    if (failedQueries == 0) logError(TAG, "Failed to query DownloadManager for ${model.fileName}", e)
                    answered = false
                    null
                }
                if (!answered) {
                    failedQueries++
                    delay(QUERY_RETRY_MILLIS)
                    continue
                }
                failedQueries = 0
                if (snapshot == null) {
                    // Removed from DownloadManager, e.g. cancelled from its notification.
                    finish(modelId, id, ModelInstallStatus.NotInstalled)
                    return@launch
                }
                when (snapshot.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val part = partFile(model)
                        val file = File(modelsFolder, model.fileName)
                        // Only now does the file get the model's name. A name that is there without a part was
                        // given by an earlier run, or by a version of the app that downloaded without parts.
                        val complete = if (part.exists()) part.length() > MIN_MODEL_BYTES && part.renameTo(file) else file.length() > MIN_MODEL_BYTES
                        val status = if (complete) {
                            ModelInstallStatus.Installed(file.absolutePath, (file.length() / MB).coerceAtLeast(1L))
                        } else {
                            part.delete()
                            file.delete()
                            ModelInstallStatus.Failed("The downloaded file was incomplete. Please try again.")
                        }
                        finish(modelId, id, status)
                        _hardwareProfile.value = readDeviceHardware(context)
                        return@launch
                    }
                    DownloadManager.STATUS_FAILED -> {
                        downloads.remove(id)
                        finish(modelId, id, ModelInstallStatus.Failed(failureMessage(snapshot.reason, model)))
                        return@launch
                    }
                    else -> {
                        val total = if (snapshot.total > 0) snapshot.total else model.sizeMb * MB
                        _installStates.update {
                            it + (modelId to ModelInstallStatus.Downloading(
                                progress = (snapshot.downloaded.toFloat() / total).coerceIn(0f, 0.99f),
                                downloadedMb = snapshot.downloaded / MB,
                                totalMb = (total / MB).coerceAtLeast(1L)
                            ))
                        }
                    }
                }
                delay(700)
            }
        }
    }

    private fun finish(modelId: String, id: Long, status: ModelInstallStatus) {
        pending.edit().remove(modelId).apply()
        trackers.remove(modelId)
        _installStates.update { it + (modelId to status) }
    }

    private fun failureMessage(reason: Int, model: LocalAiModel): String = when (reason) {
        401, 403 -> if (model.gated) {
            "Hugging Face refused the token. Accept the model's licence on huggingface.co and check the token."
        } else {
            "Hugging Face refused the download. Try again later."
        }
        404 -> "The model file is no longer on Hugging Face."
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough free storage for ${model.sizeLabel}."
        DownloadManager.ERROR_HTTP_DATA_ERROR, DownloadManager.ERROR_CANNOT_RESUME -> "The connection dropped. Please try again."
        DownloadManager.ERROR_DEVICE_NOT_FOUND, DownloadManager.ERROR_FILE_ERROR -> "The download couldn't be saved to storage."
        else -> {
            // The number is DownloadManager's own or the server's status; it means nothing to the user.
            logError(TAG, "Downloading ${model.fileName} failed with reason $reason")
            "The download failed. Please try again."
        }
    }

    override fun cancelInstall(modelId: String) {
        trackers.remove(modelId)?.cancel()
        _installStates.update { it + (modelId to ModelInstallStatus.NotInstalled) }
        scope.launch {
            val id = pending.getLong(modelId, -1L)
            if (id != -1L) runCatching { downloads.remove(id) }
            pending.edit().remove(modelId).apply()
            LocalAiCatalog.byId(modelId)?.let {
                partFile(it).delete()
                File(modelsFolder, it.fileName).delete()
            }
        }
    }

    /**
     * Where [model] is downloaded to. It only gets its own name once it is complete, so a download that is
     * still running, waiting for Wi-Fi or cut short is never taken for an installed model and loaded half-written.
     */
    private fun partFile(model: LocalAiModel): File = File(modelsFolder, model.fileName + PART_SUFFIX)

    override fun deleteModel(modelId: String) {
        trackers.remove(modelId)?.cancel()
        _installStates.update { it + (modelId to ModelInstallStatus.NotInstalled) }
        val model = LocalAiCatalog.byId(modelId) ?: return
        scope.launch {
            val id = pending.getLong(modelId, -1L)
            if (id != -1L) runCatching { downloads.remove(id) }
            pending.edit().remove(modelId).apply()
            LocalAiPaths.onModelDeleted?.invoke(model.fileName)
            partFile(model).delete()
            File(modelsFolder, model.fileName).delete()
            File(context.filesDir, MODELS_DIR).resolve(model.fileName).delete()
            File(context.filesDir, model.fileName).delete()
            val profile = readDeviceHardware(context)
            LocalAiPaths.gpuSupported = profile.gpuAccelerationSupported
            _hardwareProfile.value = profile
        }
    }

    override fun registerExternalModelFile(modelId: String, absolutePath: String) {
        val file = File(absolutePath)
        if (file.exists() && file.length() > 0L) {
            _installStates.update { it + (modelId to ModelInstallStatus.Installed(file.absolutePath, (file.length() / MB).coerceAtLeast(1L))) }
        }
    }

    override fun resolveInstalledModelPath(modelId: String): String? {
        val state = _installStates.value[modelId]
        if (state is ModelInstallStatus.Installed && File(state.path).exists()) return state.path
        return LocalAiPaths.resolveModelFile(modelId, null)?.absolutePath
    }

    private fun refreshInstalledModels() {
        _installStates.value = LocalAiCatalog.MODELS.associate { model ->
            val existing = LocalAiPaths.resolveModelFile(model.id, null)
            model.id to if (existing != null) {
                ModelInstallStatus.Installed(existing.absolutePath, (existing.length() / MB).coerceAtLeast(1L))
            } else {
                ModelInstallStatus.NotInstalled
            }
        }
    }

    private data class DownloadSnapshot(val status: Int, val reason: Int, val downloaded: Long, val total: Long)

    private companion object {
        const val MODELS_DIR = "local_models"
        const val TAG = "LocalModelController"
        const val PART_SUFFIX = ".part"
        const val MB = 1024L * 1024L
        /** How long to wait before asking DownloadManager again after it couldn't answer. */
        const val QUERY_RETRY_MILLIS = 2_000L
        /** A real `.litertlm` model is far larger than this; anything smaller is an error page. */
        const val MIN_MODEL_BYTES = 10L * MB
        const val NEEDS_TOKEN = "This model needs a Hugging Face access code. Add one below to download it."
    }
}

/** Reads the device's RAM, storage, processor/SoC, ABI and OpenCL GPU support. */
private fun readDeviceHardware(context: Context): DeviceHardwareProfile {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager?.getMemoryInfo(memoryInfo)

    val totalRamMb = ((memoryInfo.totalMem.takeIf { it > 0L } ?: Runtime.getRuntime().maxMemory()) / (1024L * 1024L))
        .coerceAtLeast(2_048L)
    val availableRamMb = ((memoryInfo.availMem.takeIf { it > 0L } ?: (totalRamMb * 512L * 1024L)) / (1024L * 1024L))
        .coerceAtLeast(512L)

    val statFs = runCatching { StatFs(context.filesDir.absolutePath) }.getOrNull()
    val freeStorageMb = ((statFs?.availableBytes ?: 0L) / (1024L * 1024L)).coerceAtLeast(0L)
    val totalStorageMb = ((statFs?.totalBytes ?: 0L) / (1024L * 1024L)).coerceAtLeast(freeStorageMb)

    val abis = Build.SUPPORTED_ABIS?.toList().orEmpty()
    val primaryAbi = abis.firstOrNull() ?: "arm64-v8a"
    val isArm64 = abis.any { it.contains("arm64", ignoreCase = true) }
    val cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

    val manufacturer = Build.MANUFACTURER?.replaceFirstChar { it.uppercase() }.orEmpty()
    val rawModel = Build.MODEL.orEmpty()
    val deviceName = if (rawModel.startsWith(manufacturer, ignoreCase = true)) {
        rawModel
    } else {
        "$manufacturer $rawModel".trim()
    }.ifBlank { "Android Phone" }

    val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Build.SOC_MODEL.takeIf { !it.isNullOrBlank() && it != Build.UNKNOWN } ?: Build.HARDWARE.orEmpty()
    } else {
        Build.HARDWARE.orEmpty()
    }.ifBlank { "ARM Processor" }

    val gpuSupported = isArm64 && hasOpenClSupport()

    return DeviceHardwareProfile(
        deviceName = deviceName,
        socOrProcessor = soc,
        cpuCores = cpuCores,
        primaryAbi = primaryAbi,
        isArm64 = isArm64,
        totalRamMb = totalRamMb,
        availableRamMb = availableRamMb,
        freeStorageMb = freeStorageMb,
        totalStorageMb = totalStorageMb,
        gpuAccelerationSupported = gpuSupported
    )
}

/** Checks whether the device has an OpenCL runtime library for LiteRT-LM's GPU backend. */
private fun hasOpenClSupport(): Boolean {
    val openClPaths = listOf(
        "/system/vendor/lib64/libOpenCL.so",
        "/vendor/lib64/libOpenCL.so",
        "/system/lib64/libOpenCL.so",
        "/system/vendor/lib64/libOpenCL-pixel.so",
        "/vendor/lib64/egl/libGLES_mali.so"
    )
    if (openClPaths.any { File(it).exists() }) return true
    return runCatching {
        System.loadLibrary("OpenCL")
        true
    }.getOrDefault(false)
}

/**
 * On-device LLM [Translator] powered by LiteRT-LM (`com.google.ai.edge.litertlm`).
 *
 * Follows the LiteRT-LM lifecycle mandates:
 * - Single-thread background executor owns `Engine` and `Conversation`.
 * - `Engine(config).initialize()` runs on that background executor, never on the main thread.
 * - Stores `engine` only after `initialize()` succeeds; never closes an engine whose `initialize()` threw.
 * - Closes `Conversation` before `Engine`, each at most once.
 */
actual class LocalAiTranslator actual constructor() : Translator {

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "LiteRtLm-SingleThread").apply { isDaemon = true }
    }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val mutex = Mutex()

    private var loadedModelPath: String? = null
    private var engine: Engine? = null

    actual override suspend fun translateAndCategorize(request: TranslationRequest): TranslationResult {
        val localEngine = request.engine as? TranslationEngine.LocalAi ?: return TranslationResult.FAILURE
        val modelFile = LocalAiPaths.resolveModelFile(localEngine.modelId, localEngine.modelPath)
            ?: return TranslationResult.failure(NO_MODEL_MESSAGE)

        val answer = withContext(dispatcher) {
            mutex.withLock { generateLocked(buildTranslationPrompt(request), modelFile, localEngine.modelId) }
        }
        val text = answer.text ?: return TranslationResult.failure(answer.errorMessage ?: "Local AI sent no answer.")
        return parseLocalModelResponse(text, request)
    }

    actual override suspend fun complete(engine: TranslationEngine, prompt: String): Completion {
        val localEngine = engine as? TranslationEngine.LocalAi ?: return Completion.failure("This provider can't answer here.")
        val modelFile = LocalAiPaths.resolveModelFile(localEngine.modelId, localEngine.modelPath)
            ?: return Completion.failure(NO_MODEL_MESSAGE)
        return withContext(dispatcher) {
            mutex.withLock { generateLocked(prompt, modelFile, localEngine.modelId) }
        }.let { answer ->
            // Reasoning models think out loud first; the answer is what follows.
            answer.text?.let { Completion(it.replace(THINK_BLOCK, "").trim().ifEmpty { it }) } ?: answer
        }
    }

    /** Loads the model into memory so the first translation doesn't wait for it; quietly does nothing if it can't. */
    actual override suspend fun prepare(engine: TranslationEngine) {
        val localEngine = engine as? TranslationEngine.LocalAi ?: return
        val modelFile = LocalAiPaths.resolveModelFile(localEngine.modelId, localEngine.modelPath) ?: return
        try {
            withContext(dispatcher) {
                mutex.withLock { ensureEngineLocked(modelFile) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Callers start this at launch without a guard of their own. Whatever stops the model from
            // loading, the next translation says so in words; it must not take the app down here.
            logError(TAG, "The local AI model couldn't be loaded ahead of time", e)
        }
    }

    /** The engine for [modelFile], loading it (or swapping the loaded model) if needed; null if it can't be loaded. */
    private fun ensureEngineLocked(modelFile: File): Engine? {
        val targetPath = modelFile.absolutePath
        if (engine != null && loadedModelPath == targetPath) return engine
        closeEngineLocked()
        val cacheDir = LocalAiPaths.cacheDir?.absolutePath ?: modelFile.parentFile?.absolutePath
        val started = TimeSource.Monotonic.markNow()
        val initialized = initializeEngineWithFallback(targetPath, cacheDir, preferGpu = LocalAiPaths.gpuSupported) ?: return null
        logInfo(TAG, "Loaded ${modelFile.name} on ${initialized.engineConfig.backend} in ${started.elapsedNow().inWholeMilliseconds} ms")
        LocalAiPaths.onModelDeleted = { fileName ->
            if (loadedModelPath?.endsWith(fileName) == true) {
                CoroutineScope(dispatcher).launch {
                    mutex.withLock {
                        if (loadedModelPath?.endsWith(fileName) == true) closeEngineLocked()
                    }
                }
            }
        }
        engine = initialized
        loadedModelPath = targetPath
        return initialized
    }

    /**
     * Sends [prompt] to the loaded model and returns its whole reply, retrying once on the CPU if the GPU backend fails.
     * [modelId] picks the model-specific way of keeping the reply short.
     */
    private suspend fun generateLocked(prompt: String, modelFile: File, modelId: String): Completion {
        val activeEngine = ensureEngineLocked(modelFile) ?: return Completion.failure(NOT_LOADED_MESSAGE)
        // Qwen 3 also honours this soft switch in the message itself, in case the template ignores ThinkingConfig.
        val fullPrompt = if (LocalAiCatalog.byId(modelId)?.family == "Qwen 3") "$prompt /no_think" else prompt
        return try {
            // A small model sometimes copies the switch into its answer.
            Completion(sendLocked(activeEngine, fullPrompt).replace("/no_think", "").trim())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError(TAG, "Local AI inference failed", e)
            // If GPU failed on first message ("Can not find OpenCL library on this device"), retry once on CPU.
            if (e.message?.contains("OpenCL", ignoreCase = true) == true) {
                LocalAiPaths.gpuSupported = false
                closeEngineLocked()
                retryOnCpuLocked(prompt, modelFile)
            } else {
                Completion.failure(NO_ANSWER_MESSAGE)
            }
        }
    }

    private suspend fun retryOnCpuLocked(prompt: String, modelFile: File): Completion {
        val cacheDir = LocalAiPaths.cacheDir?.absolutePath ?: modelFile.parentFile?.absolutePath
        val cpuEngine = initializeEngineWithFallback(modelFile.absolutePath, cacheDir, preferGpu = false)
            ?: return Completion.failure(NOT_LOADED_MESSAGE)
        engine = cpuEngine
        loadedModelPath = modelFile.absolutePath
        return try {
            Completion(sendLocked(cpuEngine, prompt))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError(TAG, "Local AI CPU fallback failed", e)
            Completion.failure(NO_ANSWER_MESSAGE)
        }
    }

    /** One conversation with [activeEngine]: [prompt] in, the whole reply out, bounded by [INFERENCE_TIMEOUT_MILLIS]. */
    private suspend fun sendLocked(activeEngine: Engine, prompt: String): String {
        var conversation: Conversation? = null
        val started = TimeSource.Monotonic.markNow()
        try {
            conversation = activeEngine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(
                        "You are an expert multilingual flashcard translator and language teacher. Follow the requested output format strictly."
                    ),
                    samplerConfig = SamplerConfig(topK = 20, topP = 0.90, temperature = 0.3),
                    // Qwen 3 and Gemma 4 reason out loud by default: hundreds of tokens, minutes on a phone,
                    // before the first word of a three-line answer. The answer alone is what we want.
                    thinkingConfig = ThinkingConfig(enableThinking = false),
                    // A flashcard answer is a few lines; a model that rambles on shouldn't keep the phone busy.
                    maxOutputToken = LOCAL_AI_MAX_OUTPUT_TOKENS
                )
            )
            val replyBuilder = StringBuilder()
            withTimeout(INFERENCE_TIMEOUT_MILLIS) {
                conversation.sendMessageAsync(prompt).collect { chunk -> replyBuilder.append(chunk.toString()) }
            }
            val reply = replyBuilder.toString().trim()
            logInfo(TAG, "Replied ${reply.length} chars in ${started.elapsedNow().inWholeMilliseconds} ms")
            return reply
        } catch (e: TimeoutCancellationException) {
            runCatching { conversation?.cancelProcess() }
            throw RuntimeException("Local AI inference timed out", e)
        } catch (e: CancellationException) {
            runCatching { conversation?.cancelProcess() }
            throw e
        } finally {
            runCatching { conversation?.close() }
        }
    }

    private fun initializeEngineWithFallback(
        modelPath: String,
        cacheDir: String?,
        preferGpu: Boolean
    ): Engine? {
        val backends = if (preferGpu) listOf(Backend.GPU(), Backend.CPU()) else listOf(Backend.CPU())
        for (backend in backends) {
            try {
                val config = EngineConfig(
                    modelPath = modelPath,
                    backend = backend,
                    cacheDir = cacheDir
                )
                // Store the engine ONLY after initialize() returns without throwing (LiteRT-LM rule).
                return Engine(config).also { it.initialize() }
            } catch (e: Exception) {
                logError(TAG, "LiteRT-LM Engine init failed on backend $backend", e)
            } catch (e: LinkageError) {
                // The native library is missing or can't be loaded on this phone (an Error, not an Exception):
                // no backend can work, and letting it through would stop the app at every start.
                logError(TAG, "LiteRT-LM's native library couldn't be loaded", e)
                return null
            }
        }
        return null
    }

    private fun closeEngineLocked() {
        val current = engine
        engine = null
        loadedModelPath = null
        if (current != null) {
            runCatching { current.close() }
        }
    }

    private companion object {
        const val TAG = "LocalAiTranslator"
        const val NO_MODEL_MESSAGE = "No Local AI model is installed yet. Install one under Settings › Translation."
        const val NOT_LOADED_MESSAGE = "The Local AI model couldn't be loaded. Close other apps to free memory and try again, or pick a lighter model in Settings."
        const val NO_ANSWER_MESSAGE = "Local AI couldn't finish its answer. Try again, or pick a lighter model in Settings."

        /** Upper bound on one local inference turn so a wedged model never deadlocks the mutex forever. */
        const val INFERENCE_TIMEOUT_MILLIS = 120_000L
    }
}

/** A model's reasoning, which isn't part of its answer; one cut short by the output cap runs to the end of the text. */
private val THINK_BLOCK = Regex("<think>[\\s\\S]*?(</think>|$)", RegexOption.IGNORE_CASE)

/**
 * Parses a local LLM's output using [parseTranslationResponse], with a graceful fallback for ultra-compact
 * models (like 0.6B) that occasionally omit the `CATEGORY:` label and output only the translated sentence.
 */
internal fun parseLocalModelResponse(rawReply: String, request: TranslationRequest): TranslationResult {
    // Strip optional <think>...</think> reasoning blocks emitted by Qwen3 / DeepSeek style models
    val cleaned = rawReply.replace(THINK_BLOCK, "").trim()
    // A small model often has no category to offer; that's no reason to lose its translation.
    val structured = parseTranslationResponse(cleaned, fallbackCategory = request.userCategory.ifBlank { DEFAULT_CATEGORY })
    if (structured.isSuccess) return structured

    // Fallback if a compact local model answered with just the translation or "TRANSLATION: ..." without CATEGORY:
    val fallbackText = cleaned
        .removePrefix("TRANSLATION:")
        .removePrefix("Translation:")
        .lines()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() && !it.startsWith("CATEGORY", ignoreCase = true) }
        ?.trim('"', '\'', '*')
        .orEmpty()

    if (fallbackText.isNotEmpty()) {
        val fallbackCategory = request.userCategory.ifBlank {
            request.existingCategories.firstOrNull() ?: DEFAULT_CATEGORY
        }
        return TranslationResult(
            translatedText = fallbackText,
            finalCategory = fallbackCategory,
            isSuccess = true,
            emoji = emojiForCategory(fallbackCategory)
        )
    }
    return TranslationResult.failure("The Local AI model returned an empty answer. Try again or pick another model.")
}
