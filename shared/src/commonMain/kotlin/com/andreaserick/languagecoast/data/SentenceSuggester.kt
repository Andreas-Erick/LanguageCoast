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
import kotlinx.coroutines.withTimeoutOrNull
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
    /**
     * What was suggested and where it came from, so the coast's [Coast.bankOffered] can be kept.
     *
     * @property error Why there are no [sentences], in words for the user, when a reason is known.
     */
    data class Suggestions(val sentences: List<ProposedSentence>, val fromBank: Boolean, val error: String? = null)

    /** What one source gave, and why it gave less than asked for, if it said. */
    private class Attempt(val sentences: List<ProposedSentence>, val error: String? = null)

    /**
     * Up to [count] sentences for [coast], none of them in [avoid] (sentences in [nativeLanguage] the user
     * already has). Empty if neither a model nor on-device translation could help; [Suggestions.error] then
     * says why, where that is known.
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
        val choice = settings.currentEngine()
        val engine = (choice as? EngineChoice.Ready)?.engine
        // What the picked provider still needs (a key, or the permission to send text to it), if anything.
        val notSetUp = (choice as? EngineChoice.Missing)?.message
        // A key is saved and the user is being asked to allow its provider: wait for the answer. Standing in with the
        // phone's translator here would fill the pool before the provider they picked gets a turn.
        if (engine == null && settings.providerAwaitingPermission().first() != null) {
            return Suggestions(emptyList(), fromBank = true, error = notSetUp)
        }
        val wording = settings.wording.first()
        var error: String? = null
        val basicsFirst = coast.bankOffered < BASICS_FROM_BANK
        if (basicsFirst) {
            val basics = fromBank(engine, coast, nativeLanguage, avoid, count, wording)
            if (basics.sentences.isNotEmpty()) return Suggestions(basics.sentences, fromBank = true)
            error = basics.error
        }
        if (engine != null && engine != TranslationEngine.OnDevice) {
            val fromModel = askModel(engine, coast, nativeLanguage, existingCategories, avoid, cardCount, count, wording)
            if (fromModel.sentences.isNotEmpty()) return Suggestions(fromModel.sentences, fromBank = false)
            error = error ?: fromModel.error
        }
        // The bank is the fallback. Where it was asked first and gave nothing, asking again would only send the same requests.
        if (basicsFirst) return Suggestions(emptyList(), fromBank = true, error = error ?: notSetUp)
        val fallback = fromBank(engine, coast, nativeLanguage, avoid, count, wording)
        return Suggestions(fallback.sentences, fromBank = true, error = if (fallback.sentences.isEmpty()) error ?: fallback.error ?: notSetUp else null)
    }

    private suspend fun askModel(
        engine: TranslationEngine,
        coast: Coast,
        nativeLanguage: String,
        existingCategories: List<String>,
        avoid: List<String>,
        cardCount: Int,
        count: Int,
        wording: Wording
    ): Attempt {
        val prompt = buildSuggestionPrompt(
            targetLanguage = coast.language,
            nativeLanguage = nativeLanguage,
            goal = coast.goal,
            count = count,
            avoid = avoid.takeLast(AVOID_LIMIT),
            existingCategories = existingCategories,
            cardCount = cardCount,
            region = coast.region,
            wording = wording
        )
        val completion = translator.complete(engine, prompt)
        val text = completion.text
        if (text == null) {
            logError(TAG, "No suggestions from the model: ${completion.errorMessage}")
            return Attempt(emptyList(), completion.errorMessage)
        }
        val avoidWords = avoid.mapTo(HashSet(), ::normalizedWords)
        return Attempt(
            parseSuggestionResponse(text)
                .filterNot { proposal -> normalizedWords(proposal.nativeText) in avoidWords }
                .take(count)
        )
    }

    /**
     * Sentences from the [PhraseBank] for the coast's goal, translated by [engine] (a model gets the topic's
     * rules and sees them as a batch), or on the device when no provider is set up; empty where neither can.
     */
    private suspend fun fromBank(
        engine: TranslationEngine?, coast: Coast, nativeLanguage: String, avoid: List<String>, count: Int, wording: Wording
    ): Attempt {
        val usable = engine ?: if (onDeviceTranslationAvailable) TranslationEngine.OnDevice else return Attempt(emptyList())
        val fillers = mapOf(PhraseBank.LANGUAGE_PLACEHOLDER to coast.language)
        val picks = PhraseBank.pick(coast.goal, avoid, count, fillers)
        val sentences = ArrayList<ProposedSentence>(picks.size)
        var error: String? = null
        for ((category, group) in picks.groupBy { it.category }) {
            val theme = PhraseBank.theme(category) ?: continue
            val translated = starterTranslator.translate(usable, theme, nativeLanguage, coast.language, coast.region, group.map { it.nativeText }, wording)
            sentences += translated
            if (translated.size < group.size) error = error ?: starterTranslator.lastError
            // A provider that translated nothing of one topic, and said why, won't do better on the next.
            if (translated.isEmpty() && starterTranslator.lastError != null) break
        }
        return Attempt(sentences, error)
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
 * model, no network) is not retried for a while, and [failures] says why it failed.
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
    private val jobs = MutableStateFlow<Map<Int, Job>>(emptyMap())
    private val failedAt = MutableStateFlow<Map<Int, Long>>(emptyMap())
    private val _preparing = MutableStateFlow<Set<Int>>(emptySet())
    private val _failures = MutableStateFlow<Map<Int, String>>(emptyMap())

    /** The coasts whose sentences are being prepared right now. */
    val preparing: StateFlow<Set<Int>> = _preparing

    /**
     * The coasts whose last top-up brought nothing, each with the reason in words for the user. A coast
     * leaves when a later top-up succeeds or with [forgetFailure], which is how the user tries again.
     */
    val failures: StateFlow<Map<Int, String>> = _failures

    /** Tops up coast [coastId] in the background, unless that is already happening. */
    fun requestTopUp(coastId: Int) {
        if (jobs.value[coastId]?.isActive == true) return
        val job = scope.launch { topUp(coastId) }
        jobs.update { it + (coastId to job) }
    }

    /** Forgets that coast [coastId]'s last top-up failed, e.g. once the provider or the goal changed, or to try again. */
    fun forgetFailure(coastId: Int) {
        failedAt.update { it - coastId }
        _failures.update { it - coastId }
    }

    /** Forgets every coast's failed top-up: a new provider, key or permission makes all their reasons out of date. */
    fun forgetFailures() {
        failedAt.value = emptyMap()
        _failures.value = emptyMap()
    }

    /**
     * Fills coast [coastId]'s pool up to a day's worth of new sentences at the user's [Pace] if it has fewer,
     * asking for at least [SUGGESTION_BATCH] at a time. A coast with that many cards still waiting to be
     * learned needs none: those come first. Does nothing while the coast's last attempt failed less than
     * [RETRY_AFTER_MILLIS] ago. One coast at a time; an attempt that takes longer than
     * [SUGGEST_TIMEOUT_MILLIS] is given up, so the other coasts never wait on one that hangs.
     */
    suspend fun topUp(coastId: Int) {
        lock.withLock { topUpLocked(coastId) }
    }

    private suspend fun topUpLocked(coastId: Int) {
        val now = clock.now().toEpochMilliseconds()
        failedAt.value[coastId]?.let { if (now - it < RETRY_AFTER_MILLIS) return }
        val coast = flashcards.observeCoast(coastId).first() ?: return
        val pace = settings.pace.first()
        if (flashcards.observeSuggestions(coastId).first().size >= pace.newPerDay) return
        val cards = flashcards.getPlacedCardsForCoast(coastId).first()
        if (cards.count { it.card.due == null } >= pace.newPerDay) return

        _preparing.update { it + coastId }
        try {
            val known = flashcards.getAllSuggestions(coastId)
            val avoid = cards.map { it.card.nativeText } + known.map { it.nativeText }
            val nativeLanguage = settings.nativeLanguage.first()
            val existingCategories = flashcards.observeIslands(coastId).first().map { it.name }
            // A translation that never comes back (a language pack that won't download, a request that stalls)
            // must not keep every coast waiting.
            val suggestions = withTimeoutOrNull(SUGGEST_TIMEOUT_MILLIS) {
                suggester.suggest(
                    coast = coast,
                    nativeLanguage = nativeLanguage,
                    existingCategories = existingCategories,
                    avoid = avoid,
                    cardCount = cards.size,
                    count = maxOf(SUGGESTION_BATCH, pace.newPerDay)
                )
            }
            if (suggestions == null) {
                logError(TAG, "Preparing sentences for coast $coastId took too long")
                // Counted from when it gave up, not from when it began: the attempt itself took the whole retry window.
                failed(coastId, clock.now().toEpochMilliseconds(), TOOK_TOO_LONG)
                return
            }
            val proposals = suggestions.sentences
            // The bank can't check the avoid list in a native language other than English, so check translations too.
            val avoidWords = avoid.mapTo(HashSet(), ::normalizedWords)
            val knownTargetWords = HashSet<List<String>>(cards.size + known.size).apply {
                cards.forEach { add(normalizedWords(it.card.targetText)) }
                known.forEach { add(normalizedWords(it.targetText)) }
            }
            val fresh = proposals
                .filterNot { proposal ->
                    normalizedWords(proposal.nativeText) in avoidWords || normalizedWords(proposal.targetText) in knownTargetWords
                }
                .distinctBy { normalizedWords(it.targetText) }
            if (fresh.isEmpty()) {
                failed(coastId, clock.now().toEpochMilliseconds(), suggestions.error ?: if (proposals.isEmpty()) NOTHING_PREPARED else NOTHING_NEW)
            } else {
                failedAt.update { it - coastId }
                _failures.update { it - coastId }
                flashcards.addSuggestions(
                    fresh.map {
                        SuggestedSentence(
                            coastId = coastId, nativeText = it.nativeText, targetText = it.targetText, category = it.category, emoji = it.emoji,
                            alternatives = it.alternatives, note = it.note
                        )
                    }
                )
                if (suggestions.fromBank) flashcards.recordBankOffered(coastId, fresh.size)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError(TAG, "Couldn't prepare sentences for coast $coastId", e)
            failed(coastId, clock.now().toEpochMilliseconds(), WENT_WRONG)
        } finally {
            _preparing.update { it - coastId }
        }
    }

    /** Remembers that coast [coastId]'s top-up brought nothing, and why. [time] is when it gave up; the wait before the next attempt counts from there. */
    private fun failed(coastId: Int, time: Long, reason: String) {
        failedAt.update { it + (coastId to time) }
        _failures.update { it + (coastId to reason) }
    }

    private companion object {
        const val TAG = "SuggestionPool"
        const val RETRY_AFTER_MILLIS = 10 * 60 * 1_000L

        /**
         * Generous on purpose: a model on a slow phone can take minutes over a batch of sentences and their
         * natural-speech check, and cutting that short would throw the work away.
         */
        const val SUGGEST_TIMEOUT_MILLIS = 10 * 60 * 1_000L

        const val TOOK_TOO_LONG = "Preparing sentences took too long. Check your connection and try again."
        const val NOTHING_PREPARED = "No sentences could be prepared. Check your connection and try again."
        const val NOTHING_NEW = "No new sentences this time. Try again later, or add your own."
        const val WENT_WRONG = "Something went wrong while preparing sentences. Try again."
    }
}
