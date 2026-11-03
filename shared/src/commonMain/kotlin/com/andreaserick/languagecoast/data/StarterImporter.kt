package com.andreaserick.languagecoast.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * How far adding starter topics to a coast has got.
 *
 * @property topic The topic being added right now, or the last one when [isFinished].
 * @property done Sentences translated so far, over every topic of this run; [total] is all of them.
 * @property added Cards saved so far.
 * @property failed Sentences that couldn't be translated.
 * @property error Why the run stopped early, worded for the user, if it did.
 */
data class ImportProgress(
    val coastId: Int,
    val topic: String,
    val done: Int,
    val total: Int,
    val added: Int = 0,
    val failed: Int = 0,
    val isFinished: Boolean = false,
    val error: String? = null,
    /** How many topics this run adds. */
    val topicCount: Int = 1
)

/**
 * Adds whole starter topics ([PhraseBank]) to a coast as islands: every sentence of the topic, with the
 * person's name, city, country and job filled in, translated in bulk by [StarterTranslator] and saved
 * on an island named after the topic. Runs in the background and reports through [progress], so the
 * user can leave the screen. One run at a time.
 */
class StarterImporter(
    private val flashcards: FlashcardRepository,
    private val settings: SettingsRepository,
    private val starterTranslator: StarterTranslator,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val _progress = MutableStateFlow<ImportProgress?>(null)
    /** The run in progress or just finished, or null when there is none (or it was dismissed). */
    val progress: StateFlow<ImportProgress?> = _progress
    private var job: Job? = null

    /** Whether a run is going on right now. */
    val isRunning: Boolean get() = job?.isActive == true

    /** Starts adding [topics] (by category) to coast [coastId] in the background, unless a run is going on. */
    fun start(coastId: Int, topics: List<String>, fillers: Map<String, String>) {
        if (isRunning) return
        job = scope.launch { run(coastId, topics, fillers) }
    }

    /** Clears a finished run from [progress]. */
    fun dismiss() {
        if (!isRunning) _progress.value = null
    }

    /**
     * The sentences of [topic] a whole-topic import can use with [fillers]: those without blanks, or whose
     * blanks are the person's (see [PROFILE_PLACEHOLDERS]) or the language. The others need a value per
     * sentence and stay in the starter sheet.
     */
    fun usableSentences(topic: PhraseBank.Theme, language: String, fillers: Map<String, String>): List<String> =
        topic.sentences.mapNotNull { PhraseBank.fill(it, fillers + (PhraseBank.LANGUAGE_PLACEHOLDER to language)) }

    /** Adds [topics] to coast [coastId] now, reporting through [progress]. */
    suspend fun run(coastId: Int, topics: List<String>, fillers: Map<String, String>) {
        val coast = flashcards.observeCoast(coastId).first() ?: return
        val themes = topics.mapNotNull(PhraseBank::theme)
        if (themes.isEmpty()) return
        val nativeLanguage = settings.nativeLanguage.first()
        val known = flashcards.observeAllCards().first().filter { it.coastId == coastId }.map { it.card.nativeText }
        val plan = themes.map { theme ->
            theme to usableSentences(theme, coast.language, fillers).filterNot { sentence -> known.any { isSameSentence(it, sentence) } }
        }
        val total = plan.sumOf { it.second.size }
        var progress = ImportProgress(coastId, themes.first().category, done = 0, total = total, topicCount = themes.size)
        _progress.value = progress

        val engine = when (val choice = settings.currentEngine()) {
            is EngineChoice.Ready -> choice.engine
            is EngineChoice.Missing -> {
                _progress.value = progress.copy(isFinished = true, error = choice.message)
                return
            }
        }

        try {
            for ((theme, sentences) in plan) {
                progress = progress.copy(topic = theme.category)
                _progress.value = progress
                val doneBefore = progress.done
                val translated = starterTranslator.translate(engine, theme, nativeLanguage, coast.language, coast.region, sentences) { done ->
                    progress = progress.copy(done = doneBefore + done)
                    _progress.value = progress
                }
                for (sentence in translated) {
                    flashcards.addFlashcard(coastId, sentence.nativeText, sentence.targetText, theme.category, theme.emoji, sentence.alternatives, sentence.note)
                }
                progress = progress.copy(done = doneBefore + sentences.size, added = progress.added + translated.size, failed = progress.failed + (sentences.size - translated.size))
                _progress.value = progress
                // A topic counts as added even if a few lines failed; the sheet still lets the user add those one by one.
                if (translated.isNotEmpty() || sentences.isEmpty()) settings.markStarterTopicAdded(coastId, theme.category)
            }
            _progress.value = progress.copy(isFinished = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError(TAG, "Adding starter topics to coast $coastId failed", e)
            _progress.value = progress.copy(isFinished = true, error = "Something went wrong while translating. ${progress.added} cards were added; try again for the rest.")
        }
    }

    private companion object {
        const val TAG = "StarterImporter"
    }
}
