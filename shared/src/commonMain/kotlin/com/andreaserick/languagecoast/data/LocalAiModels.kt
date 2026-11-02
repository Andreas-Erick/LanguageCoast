package com.andreaserick.languagecoast.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether this platform supports running local `.litertlm` AI models on the device. */
expect val localAiAvailable: Boolean

/** Runs an installed `.litertlm` model on the device for full offline translation and categorization. */
expect class LocalAiTranslator() : Translator {
    override suspend fun translateAndCategorize(request: TranslationRequest): TranslationResult
    override suspend fun complete(engine: TranslationEngine, prompt: String): Completion
    override suspend fun prepare(engine: TranslationEngine)
}

/** Classification of a phone's capability for running on-device generative AI models. */
enum class HardwareTier(val label: String, val badge: String, val summary: String) {
    COMPACT(
        label = "Compact Device",
        badge = "Entry AI",
        summary = "Best suited for ultra-compact sub-1B quantized models running on the CPU."
    ),
    MID_RANGE(
        label = "Balanced Device",
        badge = "Everyday AI",
        summary = "Comfortably runs 0.6B–1.5B local models with fast response times."
    ),
    HIGH_END(
        label = "High-Performance",
        badge = "Pro AI Ready",
        summary = "Great RAM headroom and processor capability for 1B–2B+ multilingual models."
    ),
    FLAGSHIP(
        label = "Flagship AI Device",
        badge = "Ultra AI",
        summary = "Top-tier memory and hardware acceleration — runs every local model smoothly."
    )
}

/** Snapshot of the user's phone hardware produced by scanning the device. */
data class DeviceHardwareProfile(
    val deviceName: String,
    val socOrProcessor: String,
    val cpuCores: Int,
    val primaryAbi: String,
    val isArm64: Boolean,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val freeStorageMb: Long,
    val totalStorageMb: Long,
    val gpuAccelerationSupported: Boolean,
    val tier: HardwareTier = classifyHardwareTier(totalRamMb, cpuCores, isArm64, gpuAccelerationSupported)
) {
    val totalRamGbText: String get() = formatGb(totalRamMb)
    val availableRamGbText: String get() = formatGb(availableRamMb)
    val freeStorageGbText: String get() = formatGb(freeStorageMb)
}

/** Formats megabytes as a readable GB or MB string (e.g. "7.8 GB" or "614 MB"). */
fun formatGb(megabytes: Long): String {
    if (megabytes < 1024L) return "$megabytes MB"
    val tenths = ((megabytes * 10.0) / 1024.0).toLong()
    val whole = tenths / 10
    val frac = tenths % 10
    return if (frac == 0L) "$whole GB" else "$whole.$frac GB"
}

/** Classifies a phone into a [HardwareTier] from its memory, CPU cores, architecture and GPU support. */
fun classifyHardwareTier(
    totalRamMb: Long,
    cpuCores: Int,
    isArm64: Boolean,
    gpuAccelerationSupported: Boolean
): HardwareTier = when {
    !isArm64 || totalRamMb < 4_200L -> HardwareTier.COMPACT
    totalRamMb >= 10_500L && cpuCores >= 8 && gpuAccelerationSupported -> HardwareTier.FLAGSHIP
    totalRamMb >= 7_200L -> HardwareTier.HIGH_END
    else -> HardwareTier.MID_RANGE
}

/** Metadata for a downloadable `.litertlm` model from Hugging Face `litert-community`. */
data class LocalAiModel(
    val id: String,
    val name: String,
    val family: String,
    val parameterCount: String,
    val huggingFaceRepo: String,
    val fileName: String,
    val sizeMb: Long,
    val minRamMb: Long,
    val recommendedRamMb: Long,
    val supportsGpu: Boolean,
    val badge: String,
    val summary: String,
    val strengths: List<String>,
    /** Gated on Hugging Face: the licence must be accepted there and the download needs a token. */
    val gated: Boolean = false
) {
    val downloadUrl: String
        get() = "https://huggingface.co/$huggingFaceRepo/resolve/main/$fileName"

    /** Where the licence is accepted for a [gated] model. */
    val huggingFaceUrl: String
        get() = "https://huggingface.co/$huggingFaceRepo"

    val sizeLabel: String
        get() = formatGb(sizeMb)

    val minRamLabel: String
        get() = "${formatGb(minRamMb)}+ RAM"
}

/** Curated catalog of `.litertlm` models compatible with LiteRT-LM on Android. */
object LocalAiCatalog {
    const val DEFAULT_MODEL_ID = "qwen3-0.6b"

    val MODELS: List<LocalAiModel> = listOf(
        LocalAiModel(
            id = "qwen3-0.6b",
            name = "Qwen 3 0.6B",
            family = "Qwen 3",
            parameterCount = "0.6B",
            huggingFaceRepo = "litert-community/Qwen3-0.6B",
            fileName = "Qwen3-0.6B.litertlm",
            sizeMb = 585L,
            minRamMb = 3_072L,
            recommendedRamMb = 4_096L,
            supportsGpu = true,
            badge = "Fastest · Compact",
            summary = "Ultra-lightweight multilingual model that loads in seconds and uses minimal battery and RAM.",
            strengths = listOf("Instant responses", "Low memory footprint", "30+ languages")
        ),
        LocalAiModel(
            id = "lfm2.5-1.2b",
            name = "LFM2.5 1.2B Instruct",
            family = "Liquid AI LFM2.5",
            parameterCount = "1.2B",
            huggingFaceRepo = "litert-community/LFM2.5-1.2B-Instruct",
            fileName = "LFM2.5-1.2B-Instruct_int4.litertlm",
            sizeMb = 702L,
            minRamMb = 4_096L,
            recommendedRamMb = 5_000L,
            supportsGpu = true,
            badge = "Balanced · Multilingual",
            summary = "Built for phones: quick, frugal with memory and trained on many European and Asian languages.",
            strengths = listOf("Natural phrasing", "Fast on mid-range phones", "Small download")
        ),
        LocalAiModel(
            id = "qwen3-1.7b",
            name = "Qwen 3 1.7B",
            family = "Qwen 3",
            parameterCount = "1.7B",
            huggingFaceRepo = "litert-community/Qwen3-1.7B",
            fileName = "Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm",
            sizeMb = 932L,
            minRamMb = 4_500L,
            recommendedRamMb = 6_000L,
            supportsGpu = true,
            badge = "Consistent · Reliable",
            summary = "A step up in accuracy and island sorting while still fitting most phones.",
            strengths = listOf("Strict format adherence", "Good at categories", "Many languages")
        ),
        LocalAiModel(
            id = "gemma3-1b-it",
            name = "Gemma 3 1B Instruct",
            family = "Google Gemma 3",
            parameterCount = "1B",
            huggingFaceRepo = "litert-community/Gemma3-1B-IT",
            fileName = "gemma3-1b-it-int4.litertlm",
            sizeMb = 1_024L,
            minRamMb = 4_096L,
            recommendedRamMb = 5_500L,
            supportsGpu = true,
            badge = "Gated · Needs a token",
            summary = "Google's compact instruction-tuned model. Gated on Hugging Face: accept its licence there and add a token below.",
            strengths = listOf("Great island sorting", "Natural phrasing", "Balanced size"),
            gated = true
        ),
        LocalAiModel(
            id = "gemma-4-e2b-it",
            name = "Gemma 4 E2B Instruct",
            family = "Google Gemma 4",
            parameterCount = "2B",
            huggingFaceRepo = "litert-community/gemma-4-E2B-it-litert-lm",
            fileName = "gemma-4-E2B-it.litertlm",
            sizeMb = 2_468L,
            minRamMb = 6_000L,
            recommendedRamMb = 7_500L,
            supportsGpu = true,
            badge = "High Accuracy · Rich Nuance",
            summary = "Google's flagship edge model for nuanced translations, idioms, and formal/informal alternatives.",
            strengths = listOf("Nuanced alternatives", "GPU accelerated", "Deep grammar notes")
        ),
        LocalAiModel(
            id = "phi-4-mini",
            name = "Phi-4 Mini Instruct",
            family = "Microsoft Phi-4",
            parameterCount = "3.8B",
            huggingFaceRepo = "litert-community/Phi-4-mini-instruct",
            fileName = "Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            sizeMb = 3_729L,
            minRamMb = 8_000L,
            recommendedRamMb = 11_000L,
            supportsGpu = true,
            badge = "Flagship Quality",
            summary = "Desktop-class reasoning and translation quality for flagship phones with 8 GB+ RAM.",
            strengths = listOf("Near-cloud quality", "Complex sentences", "Rich usage notes")
        )
    )

    fun byId(id: String): LocalAiModel? = MODELS.firstOrNull { it.id == id }
}

/** How well a [LocalAiModel] fits the scanned phone hardware. */
enum class ModelFitLevel(val label: String) {
    BEST_MATCH("Best Match for Your Phone"),
    GREAT_FIT("Runs Smoothly"),
    HEAVY_FIT("Playable · High RAM Use"),
    NOT_RECOMMENDED("Hardware Too Limited")
}

/** A [LocalAiModel] paired with its hardware compatibility evaluation on the user's device. */
data class ModelRecommendation(
    val model: LocalAiModel,
    val fitLevel: ModelFitLevel,
    val fitScore: Int,
    val reason: String,
    val recommendedBackend: String,
    val hasEnoughStorage: Boolean
)

/**
 * Evaluates every model in [catalog] against [profile] and returns them ordered from best match to
 * least suitable, marking the single highest-scoring compatible model as [ModelFitLevel.BEST_MATCH].
 */
fun recommendModelsForHardware(
    profile: DeviceHardwareProfile,
    catalog: List<LocalAiModel> = LocalAiCatalog.MODELS
): List<ModelRecommendation> {
    // Target sweet-spot model size based on total & available RAM.
    val idealRamTargetMb = when (profile.tier) {
        HardwareTier.FLAGSHIP -> 7_500L
        HardwareTier.HIGH_END -> 6_500L
        HardwareTier.MID_RANGE -> 5_000L
        HardwareTier.COMPACT -> 3_500L
    }

    data class Scored(
        val model: LocalAiModel,
        val rawFit: ModelFitLevel,
        val score: Int,
        val reason: String,
        val backend: String,
        val enoughStorage: Boolean
    )

    val scored = catalog.map { model ->
        val requiredStorageMb = model.sizeMb + 250L // Leave 250 MB safety buffer
        val enoughStorage = profile.freeStorageMb >= requiredStorageMb
        val backend = if (profile.gpuAccelerationSupported && model.supportsGpu) "GPU (OpenCL)" else "CPU"
        val estimatedWorkingSetMb = (model.sizeMb * 1.25).toLong()

        when {
            !profile.isArm64 -> Scored(
                model = model,
                rawFit = ModelFitLevel.NOT_RECOMMENDED,
                score = 5,
                reason = "Requires a 64-bit ARM processor (arm64-v8a); this device uses ${profile.primaryAbi}.",
                backend = "CPU",
                enoughStorage = enoughStorage
            )
            !enoughStorage -> Scored(
                model = model,
                rawFit = ModelFitLevel.NOT_RECOMMENDED,
                score = 15,
                reason = "Needs ${model.sizeLabel} free space, but only ${profile.freeStorageGbText} is available.",
                backend = backend,
                enoughStorage = false
            )
            profile.totalRamMb < model.minRamMb -> Scored(
                model = model,
                rawFit = ModelFitLevel.NOT_RECOMMENDED,
                score = 20,
                reason = "Requires at least ${formatGb(model.minRamMb)} RAM (your phone has ${profile.totalRamGbText}).",
                backend = backend,
                enoughStorage = true
            )
            profile.totalRamMb < model.recommendedRamMb || profile.availableRamMb < estimatedWorkingSetMb -> Scored(
                model = model,
                rawFit = ModelFitLevel.HEAVY_FIT,
                score = 55 - ((model.recommendedRamMb - profile.totalRamMb).coerceAtLeast(0L) / 250L).toInt(),
                reason = "Fits in ${profile.totalRamGbText} RAM (${profile.availableRamGbText} free now), though background apps may close while translating.",
                backend = backend,
                enoughStorage = true
            )
            else -> {
                val distanceFromIdeal = kotlin.math.abs(model.recommendedRamMb - idealRamTargetMb)
                val closenessBonus = (35 - (distanceFromIdeal / 150L).toInt()).coerceIn(0, 35)
                val gpuBonus = if (profile.gpuAccelerationSupported && model.supportsGpu) 10 else 0
                val totalScore = (65 + closenessBonus + gpuBonus).coerceAtMost(99)
                val reason = if (profile.gpuAccelerationSupported && model.supportsGpu) {
                    "Tailored for your ${profile.totalRamGbText} RAM and ${profile.cpuCores}-core processor with OpenCL GPU acceleration."
                } else {
                    "Runs smoothly on your ${profile.cpuCores}-core CPU with plenty of headroom in ${profile.totalRamGbText} RAM."
                }
                Scored(
                    model = model,
                    rawFit = ModelFitLevel.GREAT_FIT,
                    score = totalScore,
                    reason = reason,
                    backend = backend,
                    enoughStorage = true
                )
            }
        }
    }.sortedByDescending { it.score }

    val bestModelId = scored.firstOrNull { it.rawFit == ModelFitLevel.GREAT_FIT }?.model?.id
        ?: scored.firstOrNull { it.rawFit == ModelFitLevel.HEAVY_FIT }?.model?.id

    return scored.map { item ->
        val finalFit = if (item.model.id == bestModelId) ModelFitLevel.BEST_MATCH else item.rawFit
        val finalScore = if (item.model.id == bestModelId) 100 else item.score
        ModelRecommendation(
            model = item.model,
            fitLevel = finalFit,
            fitScore = finalScore,
            reason = item.reason,
            recommendedBackend = item.backend,
            hasEnoughStorage = item.enoughStorage
        )
    }
}

/** Installation state of a single [LocalAiModel] on the device. */
sealed interface ModelInstallStatus {
    data object NotInstalled : ModelInstallStatus
    data class Downloading(
        val progress: Float,
        val downloadedMb: Long,
        val totalMb: Long
    ) : ModelInstallStatus
    data class Installed(
        val path: String,
        val sizeMb: Long
    ) : ModelInstallStatus
    data class Failed(val message: String) : ModelInstallStatus
}

/** Scans device hardware and downloads/manages local `.litertlm` model files. */
interface LocalModelController {
    val hardwareProfile: StateFlow<DeviceHardwareProfile?>
    val isScanning: StateFlow<Boolean>
    val installStates: StateFlow<Map<String, ModelInstallStatus>>

    /** Scans or refreshes the phone's RAM, storage, CPU/SoC and GPU capability. */
    fun scanHardware()

    /**
     * Downloads and installs the `.litertlm` model with [modelId] into app storage.
     *
     * @param accessToken A Hugging Face token, needed for a [LocalAiModel.gated] model; otherwise ignored.
     */
    fun installModel(modelId: String, accessToken: String? = null)

    /** Cancels an in-progress download for [modelId] and cleans up partial files. */
    fun cancelInstall(modelId: String)

    /** Deletes an installed `.litertlm` model file for [modelId] to free storage space. */
    fun deleteModel(modelId: String)

    /** Imports a `.litertlm` file already on the device into app storage under [modelId]. */
    fun registerExternalModelFile(modelId: String, absolutePath: String)

    /** Returns the absolute file path of the installed model [modelId], or null if not installed. */
    fun resolveInstalledModelPath(modelId: String): String?

    /** Whether downloads would go over mobile data (or another metered connection) right now. */
    fun isOnMeteredNetwork(): Boolean
}

/** Default no-op implementation used when platform local model management is not active. */
class NoOpLocalModelController : LocalModelController {
    override val hardwareProfile: StateFlow<DeviceHardwareProfile?> = MutableStateFlow<DeviceHardwareProfile?>(null).asStateFlow()
    override val isScanning: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    override val installStates: StateFlow<Map<String, ModelInstallStatus>> =
        MutableStateFlow<Map<String, ModelInstallStatus>>(emptyMap()).asStateFlow()
    override fun scanHardware() = Unit
    override fun installModel(modelId: String, accessToken: String?) = Unit
    override fun cancelInstall(modelId: String) = Unit
    override fun deleteModel(modelId: String) = Unit
    override fun registerExternalModelFile(modelId: String, absolutePath: String) = Unit
    override fun resolveInstalledModelPath(modelId: String): String? = null
    override fun isOnMeteredNetwork(): Boolean = false
}
