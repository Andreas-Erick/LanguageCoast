package com.andreaserick.languagecoast.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * How far writing islands has got on a coast.
 *
 * @property island The island being written right now, or the last one when [isFinished]; [emoji] is its emoji.
 * @property done Islands finished so far, of [total].
 * @property added Cards saved so far; [addedCardIds] are theirs, so the user can look them over.
 * @property keptCardIds Studied cards that stayed when an island was written again, though the answers behind them may have changed.
 * @property empty Islands the model wrote nothing for.
 * @property stopped Whether the user stopped the run.
 * @property error Why the run stopped early, worded for the user, if it did.
 */
data class BuildProgress(
    val coastId: Int,
    val island: String,
    val emoji: String,
    val done: Int,
    val total: Int,
    val added: Int = 0,
    val addedCardIds: List<Int> = emptyList(),
    val keptCardIds: List<Int> = emptyList(),
    val empty: List<String> = emptyList(),
    val isFinished: Boolean = false,
    val stopped: Boolean = false,
    val error: String? = null
)

/** An island to write: which, and at which level (null for its own; higher takes an island about the user a level up). */
data class IslandOrder(val islandId: String, val level: Level? = null)

/**
 * Writes islands on a coast with an AI model: the islands about the user ([PersonalIslands.ALL], from what
 * they answered) and the theme islands of a level ([PersonalIslands.WRITTEN]). For each, the model writes up
 * to [PERSONAL_ISLAND_SENTENCES] sentences in both languages, a second prompt reads them as a native speaker
 * would (see [StarterTranslator.naturalCheck]), and they are saved on an island of that name. Runs in the
 * background and reports through [progress]. One run at a time; [stop] ends it after the island in hand.
 *
 * Writing an island again (after the answers changed) replaces the cards on it that were never studied
 * and keeps the others, which the new sentences then steer clear of. Writing it a level up only adds.
 */
class PersonalIslandBuilder(
    private val flashcards: FlashcardRepository,
    private val settings: SettingsRepository,
    private val translator: Translator,
    private val starterTranslator: StarterTranslator,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val _progress = MutableStateFlow<BuildProgress?>(null)
    /** The run in progress or just finished, or null when there is none (or it was dismissed). */
    val progress: StateFlow<BuildProgress?> = _progress
    private var job: Job? = null

    /** Whether a run is going on right now. */
    val isRunning: Boolean get() = job?.isActive == true

    /** Starts writing [orders] on coast [coastId] in the background, unless a run is going on. */
    fun start(coastId: Int, orders: List<IslandOrder>) {
        if (isRunning) return
        job = scope.launch { run(coastId, orders) }
    }

    /** Stops the run. What was written so far stays; the island in hand is dropped. */
    fun stop() {
        job?.cancel()
    }

    /** Clears a finished run from [progress]. */
    fun dismiss() {
        if (!isRunning) _progress.value = null
    }

    /** Writes [orders] on coast [coastId] now, reporting through [progress]. Islands that can't be written yet are left out. */
    suspend fun run(coastId: Int, orders: List<IslandOrder>) {
        val coast = flashcards.observeCoast(coastId).first() ?: return
        val profile = settings.profile.first()
        val answers = settings.personalAnswers.first()
        val wording = settings.wording.first()
        val plan = orders.mapNotNull { order -> PersonalIslands.byId(order.islandId)?.let { it to order.level?.takeIf { level -> level != it.level } } }
            .filter { (island, _) -> island.isUnlocked(answers) }
        if (plan.isEmpty()) return
        val nativeLanguage = settings.nativeLanguage.first()
        var progress = BuildProgress(coastId, plan.first().first.name, plan.first().first.emoji, done = 0, total = plan.size)
        _progress.value = progress

        val ai = when (val connection = AskAi(translator, settings).connect()) {
            is AiConnection.Ready -> connection
            is AiConnection.Unavailable -> {
                _progress.value = progress.copy(isFinished = true, error = connection.message)
                return
            }
        }

        try {
            for ((island, raisedTo) in plan) {
                progress = progress.copy(island = island.name, emoji = island.emoji)
                _progress.value = progress
                val key = builtKey(island.id, raisedTo)
                val built = settings.personalIslandsBuilt.first()[personalIslandKey(coastId, key)] != null
                val coastCards = flashcards.getPlacedCardsForCoast(coastId).first()
                val onIsland = coastCards.filter { it.islandName == island.name }.map { it.card }
                // Only an island this builder wrote has cards it may replace; one the user made by hand is only added to,
                // and so is an island taken a level up, whose simpler sentences stay.
                val replaceable = if (built && raisedTo == null) onIsland.filter { it.lastReviewed == null } else emptyList()
                val kept = onIsland - replaceable.toSet()
                // A starter topic may already say some of it ("My name is Anna."); the island shouldn't say it again.
                val elsewhere = coastCards.filter { it.islandName in island.related }.map { it.card.nativeText }

                val written = write(
                    ai, island, island.facts(profile, answers), nativeLanguage, coast,
                    avoid = kept.map { it.nativeText } + elsewhere, level = raisedTo ?: island.level, wording = wording
                )
                if (written is Written.Failed) {
                    _progress.value = progress.copy(
                        isFinished = true,
                        error = "${written.message} ${if (progress.done == 0) "Nothing was changed." else "${progress.done} of ${progress.total} islands were written; try again for the rest."}"
                    )
                    return
                }
                val sentences = (written as Written.Sentences).sentences
                if (sentences.isEmpty()) {
                    progress = progress.copy(done = progress.done + 1, empty = progress.empty + island.name)
                    _progress.value = progress
                    continue
                }
                // Saved whole or not at all: a Stop that lands here waits until the island's old cards are replaced
                // by the new ones and the island is marked as written, so it is never left half-written.
                val added = withContext(NonCancellable) {
                    val cards = flashcards.replaceIslandCards(coastId, island.name, island.emoji, replaceable, sentences)
                    settings.markPersonalIslandBuilt(coastId, key, island.fingerprint(profile, answers))
                    cards
                }
                progress = progress.copy(
                    done = progress.done + 1,
                    added = progress.added + added.size,
                    addedCardIds = progress.addedCardIds + added,
                    // Studied cards that outlived a rewrite may say what is no longer true; the user gets to look at them.
                    keptCardIds = progress.keptCardIds + if (built && raisedTo == null) kept.map { it.cardId } else emptyList()
                )
                _progress.value = progress
            }
            _progress.value = progress.copy(isFinished = true)
        } catch (e: CancellationException) {
            _progress.value = progress.copy(isFinished = true, stopped = true)
            throw e
        } catch (e: Exception) {
            logError(TAG, "Writing islands on coast $coastId failed", e)
            _progress.value = progress.copy(isFinished = true, error = "Something went wrong while writing. ${progress.done} of ${progress.total} islands were written; try again for the rest.")
        }
    }

    private sealed interface Written {
        data class Sentences(val sentences: List<ProposedSentence>) : Written
        data class Failed(val message: String) : Written
    }

    /**
     * The island's sentences from the model, checked for natural speech. A cloud model writes them in one
     * prompt; a model on the phone can only write a few lines per answer, so it is asked in rounds until it
     * has no more to say.
     */
    private suspend fun write(
        ai: AiConnection.Ready,
        island: PersonalIsland,
        facts: List<Pair<String, String>>,
        nativeLanguage: String,
        coast: Coast,
        avoid: List<String>,
        level: Level,
        wording: Wording
    ): Written {
        val perPrompt = if (ai.isLocal) LOCAL_SENTENCES_PER_PROMPT else PERSONAL_ISLAND_SENTENCES
        val sentences = ArrayList<ProposedSentence>(PERSONAL_ISLAND_SENTENCES)
        while (sentences.size < PERSONAL_ISLAND_SENTENCES) {
            val wanted = minOf(perPrompt, PERSONAL_ISLAND_SENTENCES - sentences.size)
            val taken = avoid + sentences.map { it.nativeText }
            val prompt = buildPersonalIslandPrompt(
                island, facts, nativeLanguage, coast.language, coast.region, coast.goal, wanted, taken.takeLast(AVOID_LIMIT), level, wording
            )
            // Several islands are many requests in a row; a provider's per-minute limit is waited out instead of ending the run.
            val reply = ai.ask(prompt, waitWhileBusy = true) { parsePersonalIslandResponse(it, island, wanted, taken) }
            val fresh = reply.value
            if (fresh == null) {
                logError(TAG, "No sentences for ${island.name}: ${reply.error}")
                if (sentences.isEmpty()) return Written.Failed(reply.error ?: noAnswer(ai.provider))
                break
            }
            // A small model sometimes repeats the sentence instead of translating it; such a card would teach nothing.
            sentences += fresh.filterNot { nativeLanguage != coast.language && it.nativeText.length > 6 && isSameSentence(it.nativeText, it.targetText) }
            // Fewer than asked for means the model has said what there is to say.
            if (fresh.size < wanted) break
        }
        val theme = PhraseBank.Theme(island.name, island.emoji, emptyList(), emptyList())
        val checked = sentences.chunked(StarterTranslator.BATCH_SIZE).flatMap { batch ->
            starterTranslator.naturalCheck(ai.engine, theme, nativeLanguage, coast.language, coast.region, batch)
        }
        return Written.Sentences(checked)
    }

    companion object {
        private const val TAG = "PersonalIslandBuilder"
        /** A model on the phone answers in at most a few hundred tokens: this many two-sided lines fit. */
        const val LOCAL_SENTENCES_PER_PROMPT = 8
        /** How many sentences the island already has are listed in a prompt at most. */
        private const val AVOID_LIMIT = 60
        const val NEEDS_MODEL_MESSAGE = "These islands are written by an AI model. Pick an AI provider under Settings › Translation first."
    }
}
