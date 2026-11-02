package com.andreaserick.languagecoast.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

actual val localAiAvailable = true

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

    fun resolveModelFile(modelId: String, explicitPath: String?): File? {
        if (!explicitPath.isNullOrBlank()) {
            val file = File(explicitPath)
            if (file.exists() && file.length() > 0L) return file
        }
        val spec = LocalAiCatalog.byId(modelId) ?: LocalAiCatalog.MODELS.first()
        val candidates = listOfNotNull(
            modelsDir?.resolve(spec.fileName),
            filesDir?.resolve(spec.fileName),
            filesDir?.resolve("model.litertlm")
        )
        return candidates.firstOrNull { it.exists() && it.length() > 0L }
    }
}

/**
 * Android implementation of [LocalModelController]: inspects phone RAM, storage, CPU/SoC and OpenCL GPU
 * support, and downloads `.litertlm` models into `filesDir/local_models`.
 */
class AndroidLocalModelController(
    private val context: Context,
    private val client: HttpClient
) : LocalModelController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val modelsFolder = File(context.filesDir, "local_models").apply { mkdirs() }
    private val downloadJobs = mutableMapOf<String, Job>()

    private val _hardwareProfile = MutableStateFlow<DeviceHardwareProfile?>(null)
    override val hardwareProfile: StateFlow<DeviceHardwareProfile?> = _hardwareProfile.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _installStates = MutableStateFlow<Map<String, ModelInstallStatus>>(emptyMap())
    override val installStates: StateFlow<Map<String, ModelInstallStatus>> = _installStates.asStateFlow()

    init {
        LocalAiPaths.modelsDir = modelsFolder
        LocalAiPaths.filesDir = context.filesDir
        LocalAiPaths.cacheDir = context.cacheDir
        refreshInstalledModels()
        scanHardware()
    }

    override fun scanHardware() {
        scope.launch {
            _isScanning.value = true
            delay(350) // Brief scan window so the UI shows live progress feedback when re-scanning
            val profile = readDeviceHardware(context)
            LocalAiPaths.gpuSupported = profile.gpuAccelerationSupported
            _hardwareProfile.value = profile
            refreshInstalledModels()
            _isScanning.value = false
        }
    }

    override fun installModel(modelId: String) {
        val model = LocalAiCatalog.byId(modelId) ?: return
        if (downloadJobs[modelId]?.isActive == true) return

        val job = scope.launch {
            val partFile = File(modelsFolder, "${model.fileName}.part")
            val targetFile = File(modelsFolder, model.fileName)
            _installStates.update {
                it + (modelId to ModelInstallStatus.Downloading(progress = 0f, downloadedMb = 0L, totalMb = model.sizeMb))
            }

            try {
                client.prepareGet(model.downloadUrl).execute { response ->
                    if (!response.status.isSuccess()) {
                        val message = when (response.status.value) {
                            401, 403 -> "This model requires Hugging Face access or is gated. Try Qwen 3 0.6B or Gemma 4 E2B."
                            404 -> "Model file was not found on the server (${response.status.value})."
                            else -> "Download failed with HTTP ${response.status.value}."
                        }
                        _installStates.update { it + (modelId to ModelInstallStatus.Failed(message)) }
                        return@execute
                    }

                    val contentLength = response.contentLength() ?: (model.sizeMb * 1024L * 1024L)
                    val totalMb = (contentLength / (1024L * 1024L)).coerceAtLeast(1L)
                    val channel = response.bodyAsChannel()
                    val buffer = ByteArray(64 * 1024)
                    var downloadedBytes = 0L
                    var lastEmitMs = 0L

                    FileOutputStream(partFile).use { output ->
                        while (!channel.isClosedForRead) {
                            val read = channel.readAvailable(buffer, 0, buffer.size)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            val now = System.currentTimeMillis()
                            if (now - lastEmitMs >= 120L) {
                                lastEmitMs = now
                                val downloadedMb = downloadedBytes / (1024L * 1024L)
                                val progress = (downloadedBytes.toFloat() / contentLength.toFloat()).coerceIn(0f, 0.99f)
                                _installStates.update {
                                    it + (modelId to ModelInstallStatus.Downloading(progress, downloadedMb, totalMb))
                                }
                            }
                        }
                        output.flush()
                    }
                }

                // Verify we didn't already fail inside execute
                if (_installStates.value[modelId] is ModelInstallStatus.Failed) {
                    partFile.delete()
                    return@launch
                }

                // Sanity check: a real .litertlm model is at least several megabytes
                if (!partFile.exists() || partFile.length() < 10L * 1024L * 1024L) {
                    partFile.delete()
                    _installStates.update {
                        it + (modelId to ModelInstallStatus.Failed("Downloaded file was incomplete. Check your connection and try again."))
                    }
                    return@launch
                }

                if (targetFile.exists()) targetFile.delete()
                val renamed = partFile.renameTo(targetFile)
                if (!renamed) {
                    partFile.copyTo(targetFile, overwrite = true)
                    partFile.delete()
                }

                val finalSizeMb = (targetFile.length() / (1024L * 1024L)).coerceAtLeast(1L)
                _installStates.update {
                    it + (modelId to ModelInstallStatus.Installed(targetFile.absolutePath, finalSizeMb))
                }
                // Update free storage after installing a large model file
                _hardwareProfile.value = readDeviceHardware(context)
            } catch (e: CancellationException) {
                partFile.delete()
                _installStates.update { it + (modelId to ModelInstallStatus.NotInstalled) }
                throw e
            } catch (e: Exception) {
                logError(TAG, "Failed to download model ${model.id}", e)
                partFile.delete()
                _installStates.update {
                    it + (modelId to ModelInstallStatus.Failed("Download interrupted: ${e.message ?: "check your internet connection"}"))
                }
            }
        }
        downloadJobs[modelId] = job
    }

    override fun cancelInstall(modelId: String) {
        downloadJobs.remove(modelId)?.cancel()
        val model = LocalAiCatalog.byId(modelId)
        if (model != null) {
            File(modelsFolder, "${model.fileName}.part").delete()
        }
        _installStates.update { it + (modelId to ModelInstallStatus.NotInstalled) }
    }

    override fun deleteModel(modelId: String) {
        cancelInstall(modelId)
        val model = LocalAiCatalog.byId(modelId) ?: return
        File(modelsFolder, model.fileName).delete()
        File(context.filesDir, model.fileName).delete()
        _installStates.update { it + (modelId to ModelInstallStatus.NotInstalled) }
        _hardwareProfile.value = readDeviceHardware(context)
    }

    override fun registerExternalModelFile(modelId: String, absolutePath: String) {
        val file = File(absolutePath)
        if (file.exists() && file.length() > 0L) {
            val sizeMb = (file.length() / (1024L * 1024L)).coerceAtLeast(1L)
            _installStates.update {
                it + (modelId to ModelInstallStatus.Installed(file.absolutePath, sizeMb))
            }
        }
    }

    override fun resolveInstalledModelPath(modelId: String): String? {
        val state = _installStates.value[modelId]
        if (state is ModelInstallStatus.Installed && File(state.path).exists()) {
            return state.path
        }
        return LocalAiPaths.resolveModelFile(modelId, null)?.absolutePath
    }

    private fun refreshInstalledModels() {
        val updated = mutableMapOf<String, ModelInstallStatus>()
        for (model in LocalAiCatalog.MODELS) {
            val current = _installStates.value[model.id]
            if (current is ModelInstallStatus.Downloading) {
                updated[model.id] = current
                continue
            }
            val existing = LocalAiPaths.resolveModelFile(model.id, null)
            if (existing != null) {
                val sizeMb = (existing.length() / (1024L * 1024L)).coerceAtLeast(1L)
                updated[model.id] = ModelInstallStatus.Installed(existing.absolutePath, sizeMb)
            } else {
                updated[model.id] = ModelInstallStatus.NotInstalled
            }
        }
        _installStates.value = updated
    }

    private companion object {
        const val TAG = "AndroidLocalModelController"
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
            ?: return TranslationResult.failure(
                "No local AI model is installed yet. Open Settings → Translation → Local AI to scan your phone and install a model."
            )

        return withContext(dispatcher) {
            mutex.withLock {
                runInferenceLocked(request, modelFile)
            }
        }
    }

    private suspend fun runInferenceLocked(request: TranslationRequest, modelFile: File): TranslationResult {
        val targetPath = modelFile.absolutePath
        val cacheDir = LocalAiPaths.cacheDir?.absolutePath ?: modelFile.parentFile?.absolutePath

        if (engine == null || loadedModelPath != targetPath) {
            closeEngineLocked()
            val initialized = initializeEngineWithFallback(targetPath, cacheDir, preferGpu = LocalAiPaths.gpuSupported)
                ?: return TranslationResult.failure(
                    "Couldn't load the local AI model (${modelFile.name}). Ensure your phone has enough free RAM and try again."
                )
            engine = initialized
            loadedModelPath = targetPath
        }

        val activeEngine = checkNotNull(engine)
        var conversation: Conversation? = null
        return try {
            conversation = activeEngine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(
                        "You are an expert multilingual flashcard translator. Follow the requested output format strictly."
                    ),
                    samplerConfig = SamplerConfig(topK = 20, topP = 0.90, temperature = 0.3)
                )
            )
            val prompt = buildTranslationPrompt(request)
            val replyBuilder = StringBuilder()
            conversation.sendMessageAsync(prompt).collect { chunk ->
                replyBuilder.append(chunk.toString())
            }
            val rawReply = replyBuilder.toString().trim()
            parseLocalModelResponse(rawReply, request)
        } catch (e: CancellationException) {
            runCatching { conversation?.cancelProcess() }
            throw e
        } catch (e: Exception) {
            logError(TAG, "Local AI inference failed", e)
            // If GPU failed on first message ("Can not find OpenCL library on this device"), retry once on CPU!
            if (e.message?.contains("OpenCL", ignoreCase = true) == true) {
                LocalAiPaths.gpuSupported = false
                runCatching { conversation?.close() }
                conversation = null
                closeEngineLocked()
                return retryOnCpuLocked(request, targetPath, cacheDir)
            }
            TranslationResult.failure("Local AI couldn't finish translating: ${e.message ?: "try a lighter model in Settings."}")
        } finally {
            runCatching { conversation?.close() }
        }
    }

    private suspend fun retryOnCpuLocked(
        request: TranslationRequest,
        targetPath: String,
        cacheDir: String?
    ): TranslationResult {
        val cpuEngine = initializeEngineWithFallback(targetPath, cacheDir, preferGpu = false)
            ?: return TranslationResult.failure("Local AI couldn't initialize on CPU.")
        engine = cpuEngine
        loadedModelPath = targetPath
        var conversation: Conversation? = null
        return try {
            conversation = cpuEngine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(
                        "You are an expert multilingual flashcard translator. Follow the requested output format strictly."
                    ),
                    samplerConfig = SamplerConfig(topK = 20, topP = 0.90, temperature = 0.3)
                )
            )
            val replyBuilder = StringBuilder()
            conversation.sendMessageAsync(buildTranslationPrompt(request)).collect { chunk ->
                replyBuilder.append(chunk.toString())
            }
            parseLocalModelResponse(replyBuilder.toString().trim(), request)
        } catch (e: CancellationException) {
            runCatching { conversation?.cancelProcess() }
            throw e
        } catch (e: Exception) {
            logError(TAG, "Local AI CPU fallback failed", e)
            TranslationResult.failure("Local AI error: ${e.message ?: "couldn't run model"}")
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
    }
}

/**
 * Parses a local LLM's output using [parseTranslationResponse], with a graceful fallback for ultra-compact
 * models (like 0.6B) that occasionally omit the `CATEGORY:` label and output only the translated sentence.
 */
internal fun parseLocalModelResponse(rawReply: String, request: TranslationRequest): TranslationResult {
    // Strip optional <think>...</think> reasoning blocks emitted by Qwen3 / DeepSeek style models
    val cleaned = rawReply.replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "").trim()
    val structured = parseTranslationResponse(cleaned)
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
    return TranslationResult.failure("The local AI model returned an empty answer. Try again or pick another model.")
}
