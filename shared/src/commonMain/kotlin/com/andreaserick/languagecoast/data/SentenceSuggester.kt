package com.andreaserick.languagecoast.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * Comes up with sentences worth learning next on a coast. The basics come first: until [BASICS_FROM_BANK]
 * starter sentences were offered, they come from the [PhraseBank] (the topics the goal points to first),
 * translated by the AI provider picked in Settings or on the device. After that the AI provider, when it
 * is a language model, suggests sentences fitted to the goal; the bank stays the fallback.
 */
class SentenceSuggester(
    private val translator: Translator,
    private val settings: SettingsRepository,
    private val starterTranslator: StarterTranslator
) {
    /** What was suggested and where it came from, so the coast's [Coast.bankOffered] can be kept. */
    data class Suggestions(val sentences: List<ProposedSentence>, val fromBank: Boolean)

    /**
     * Up to [count] sentences for [coast], none of them in [avoid] (sentences in [nativeLanguage] the user
     * already has). Empty if neither a model nor on-device translation could help.
     *
     * @param existingCategories The coast's islands, so sentences land on them where one fits.
     * @param cardCount How many cards the coast has, as a rough level.
     */
    suspend fun suggest(
        coast: Coast,
        nativeLanguage: String,
        existingCategories: List<String>,
        avoid: List<String>,
        cardCount: Int,
        count: Int
    ): Suggestions {
        val engine = (settings.currentEngine() as? EngineChoice.Ready)?.engine
        if (coast.bankOffered < BASICS_FROM_BANK) {
            val basics = fromBank(engine, coast, nativeLanguage, avoid, count)
            if (basics.isNotEmpty()) return Suggestions(basics, fromBank = true)
        }
        val fromModel = if (engine != null && engine != TranslationEngine.OnDevice) {
            askModel(engine, coast, nativeLanguage, existingCategories, avoid, cardCount, count)
        } else emptyList()
        if (fromModel.isNotEmpty()) return Suggestions(fromModel, fromBank = false)
        return Suggestions(fromBank(engine, coast, nativeLanguage, avoid, count), fromBank = true)
    }

    private suspend fun askModel(
        engine: TranslationEngine,
        coast: Coast,
        nativeLanguage: String,
        existingCategories: List<String>,
        avoid: List<String>,
        cardCount: Int,
        count: Int
    ): List<ProposedSentence> {
        val prompt = buildSuggestionPrompt(
            targetLanguage = coast.language,
            nativeLanguage = nativeLanguage,
            goal = coast.goal,
            count = count,
            avoid = avoid.takeLast(AVOID_LIMIT),
            existingCategories = existingCategories,
            cardCount = cardCount
        )
        val completion = translator.complete(engine, prompt)
        val text = completion.text
        if (text == null) {
            logError(TAG, "No suggestions from the model: ${completion.errorMessage}")
            return emptyList()
        }
        return parseSuggestionResponse(text)
            .filterNot { proposal -> avoid.any { isSameSentence(it, proposal.nativeText) } }
            .take(count)
    }

    /**
     * Sentences from the [PhraseBank] for the coast's goal, translated by [engine] (a model gets the topic's
     * rules and sees them as a batch), or on the device when no provider is set up; empty where neither can.
     */
    private suspend fun fromBank(engine: TranslationEngine?, coast: Coast, nativeLanguage: String, avoid: List<String>, count: Int): List<ProposedSentence> {
        val usable = engine ?: if (onDeviceTranslationAvailable) TranslationEngine.OnDevice else return emptyList()
        val fillers = mapOf(PhraseBank.LANGUAGE_PLACEHOLDER to coast.language)
        val picks = PhraseBank.pick(coast.goal, avoid, count, fillers)
        return picks.groupBy { it.category }.flatMap { (category, group) ->
            val theme = PhraseBank.theme(category) ?: return@flatMap emptyList()
            starterTranslator.translate(usable, theme, nativeLanguage, coast.language, group.map { it.nativeText })
        }
    }

    private companion object {
        const val TAG = "SentenceSuggester"
        /** The prompt only lists this many sentences to avoid; beyond that it mostly costs tokens. */
        const val AVOID_LIMIT = 60
    }
}

/**
 * Keeps a few suggested sentences ready for every coast's daily session, so the session never waits for
 * the AI. [requestTopUp] fills a coast's pool in the background whenever it runs low; a failure (no
 * model, no network) is not retried for a while.
 */
class SuggestionPool(
    private val flashcards: FlashcardRepository,
    private val settings: SettingsRepository,
    private val suggester: SentenceSuggester,
    private val clock: Clock,
    /** Where background top-ups run; tests pass a scope of their own. */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val lock = Mutex()
    private val jobs = HashMap<Int, Job>()
    private val failedAt = HashMap<Int, Long>()
    private val _preparing = MutableStateFlow<Set<Int>>(emptySet())

    /** The coasts whose sentences are being prepared right now. */
    val preparing: StateFlow<Set<Int>> = _preparing

    /** Tops up coast [coastId] in the background, unless that is already happening. */
    fun requestTopUp(coastId: Int) {
        if (jobs[coastId]?.isActive == true) return
        jobs[coastId] = scope.launch { topUp(coastId) }
    }

    /** Forgets that coast [coastId]'s last top-up failed, e.g. once the provider or the goal changed. */
    fun forgetFailure(coastId: Int) {
        failedAt.remove(coastId)
    }

    /**
     * Fills coast [coastId]'s pool up to [DAILY_NEW_SENTENCES] ready sentences if it has fewer, asking for
     * [SUGGESTION_BATCH] at a time. Does nothing while the coast's last attempt failed less than
     * [RETRY_AFTER_MILLIS] ago.
     */
    suspend fun topUp(coastId: Int) = lock.withLock {
        val coast = flashcards.observeCoast(coastId).first() ?: return
        if (flashcards.observeSuggestions(coastId).first().size >= DAILY_NEW_SENTENCES) return
        val now = clock.now().toEpochMilliseconds()
        failedAt[coastId]?.let { if (now - it < RETRY_AFTER_MILLIS) return }

        _preparing.update { it + coastId }
        try {
            val cards = flashcards.observeAllCards().first().filter { it.coastId == coastId }
            val known = flashcards.getAllSuggestions(coastId)
            val avoid = cards.map { it.card.nativeText } + known.map { it.nativeText }
            val suggestions = suggester.suggest(
                coast = coast,
                nativeLanguage = settings.nativeLanguage.first(),
                existingCategories = flashcards.observeIslands(coastId).first().map { it.name },
                avoid = avoid,
                cardCount = cards.size,
                count = SUGGESTION_BATCH
            )
            val proposals = suggestions.sentences
            // The bank can't check the avoid list in a native language other than English, so check translations too.
            val knownTargets = cards.map { it.card.targetText } + known.map { it.targetText }
            val fresh = proposals
                .filterNot { proposal ->
                    avoid.any { isSameSentence(it, proposal.nativeText) } || knownTargets.any { isSameSentence(it, proposal.targetText) }
                }
                .distinctBy { normalizedWords(it.targetText) }
            if (fresh.isEmpty()) {
                failedAt[coastId] = now
            } else {
                failedAt.remove(coastId)
                flashcards.addSuggestions(
                    fresh.map { SuggestedSentence(coastId = coastId, nativeText = it.nativeText, targetText = it.targetText, category = it.category, emoji = it.emoji) }
                )
                if (suggestions.fromBank) flashcards.recordBankOffered(coastId, fresh.size)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError(TAG, "Couldn't prepare sentences for coast $coastId", e)
            failedAt[coastId] = now
        } finally {
            _preparing.update { it - coastId }
        }
    }

    private companion object {
        const val TAG = "SuggestionPool"
        const val RETRY_AFTER_MILLIS = 10 * 60 * 1_000L
    }
}
