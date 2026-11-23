package com.andreaserick.languagecoast.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.math.ln
import kotlin.random.Random
import kotlin.time.Clock

/** A practice sheet, or why there is none. */
sealed interface WorksheetResult {
    data class Ready(val worksheet: Worksheet) : WorksheetResult
    data class Failed(val message: String) : WorksheetResult
}

/**
 * Writes a coast's practice sheet with the AI provider picked in Settings, a batch of sentences per request
 * ([worksheetRequests]), each request being told what is on the sheet already. It asks for a few sentences
 * more than the sheet needs and leaves out the weakest ([pickWorksheetSentences]). A model that sends too
 * few is asked again, but not forever: a small coast only has so many sentences in it, so a long sheet can
 * come out shorter than asked for.
 *
 * What a sheet practises follows from how the coast's cards have been studied: every sentence is built from
 * one card, the islands whose cards are slipping or keep being forgotten get the most sentences
 * (`WorksheetFocus.kt`), and of an island's cards the ones that need practice most come first
 * ([WorksheetCardDealer]). The request also carries the level the learner has reached on the coast's route
 * and how they speak of themselves (as a woman, as a man), as the other writing requests do.
 *
 * A coast of any size works: a prompt never carries more than a fixed number of cards and characters, of a
 * large coast it shows the islands its sentences are about ([WorksheetCardDealer]), and a local model, which
 * reads a short prompt, gets a few islands at a time. A long sheet from a large coast is many large requests in a
 * row, which a provider's per-minute limit can turn away; the sheet then waits as long as the provider says
 * ([Completion.retryAfterSeconds]) and carries on, instead of coming out short.
 */
class WorksheetGenerator(
    private val translator: Translator,
    private val settings: SettingsRepository,
    private val flashcards: FlashcardRepository,
    private val clock: Clock,
    private val timeZone: TimeZone,
    /** Picks the cards a prompt shows when the coast has more than fit; tests fix it. */
    private val random: Random = Random.Default,
    /** Where the cards are sorted through: off the screen's thread, which thousands of cards would hold up. */
    private val background: CoroutineDispatcher = Dispatchers.Default
) {
    /**
     * A sheet for coast [coastId] as [options] ask.
     *
     * @param onProgress Called with how many sentences are written so far, after each answer.
     */
    suspend fun generate(coastId: Int, options: WorksheetOptions, onProgress: (Int) -> Unit = {}): WorksheetResult {
        val coast = flashcards.observeCoast(coastId).first() ?: return WorksheetResult.Failed("This coast is gone.")
        // This coast's cards only, and of a card only what a sheet is made from: there can be tens of thousands.
        // Of those, the cards of the islands the user picked: the sheet's words and topics come from nowhere else.
        val cards = flashcards.getWorksheetCards(coastId).filter { options.includes(it.islandId) }
        val cardCount = cards.size
        if (cardCount < WORKSHEET_MIN_CARDS) {
            val custom = options.kind == SheetKind.Custom
            return WorksheetResult.Failed(
                when {
                    custom && options.islandIds == null -> "A custom sheet needs at least $WORKSHEET_MIN_CARDS cards on the coast to take its words from."
                    custom -> "A custom sheet needs at least $WORKSHEET_MIN_CARDS cards to take its words from. Pick more islands."
                    options.islandIds == null -> "A practice sheet needs at least $WORKSHEET_MIN_CARDS cards on the coast to make new sentences from."
                    else -> "A practice sheet needs at least $WORKSHEET_MIN_CARDS cards to make new sentences from. Pick more islands."
                }
            )
        }
        val ai = when (val connection = AskAi(translator, settings).connect()) {
            is AiConnection.Unavailable -> return WorksheetResult.Failed(connection.message)
            is AiConnection.Ready -> connection
        }
        val provider = ai.provider

        val nativeLanguage = settings.nativeLanguage.first()
        val wording = settings.wording.first()
        val history = settings.worksheetHistory(coastId).first()
        // The route's level when the sheet's cards are mostly the route's; otherwise an empirical estimate once
        // enough custom/imported cards have been studied and retained (null on a freshly imported deck).
        val nowMillis = clock.now().toEpochMilliseconds()
        val route = observeRoute(flashcards, settings, coastId, nowMillis).first()
        val routeIslands = route.levels.flatMap { it.stops }.filter { it.isOnCoast }.mapTo(HashSet()) { it.name }
        val level = if (cards.count { it.islandName in routeIslands } * 2 >= cards.size) {
            route.current.level
        } else {
            estimateWorksheetLevel(cards)
        }
        val count = options.count.coerceIn(WORKSHEET_COUNT_RANGE)
        val capacity = ai.capacity
        val limits = capacity.sheetPrompt
        val alternativesByCard = HashMap<WorksheetCard, List<String>>()
        val (known, vocabulary, dealer) = withContext(background) {
            val known = ArrayList<String>(cards.size)
            cards.forEach {
                known += it.targetText
                known += it.alternatives
                if (it.alternatives.isNotEmpty()) {
                    val key = WorksheetCard(it.islandName, WorksheetCardDealer.oneLine(it.targetText), WorksheetCardDealer.oneLine(it.nativeText))
                    alternativesByCard[key] = it.alternatives
                }
            }
            Triple(
                known,
                CoastVocabulary(known),
                WorksheetCardDealer(
                    cards = cards,
                    limit = limits.cards,
                    perIsland = limits.cardsPerIsland,
                    characters = limits.characters,
                    now = nowMillis,
                    random = random,
                    recentAnchors = history.anchoredCards.toHashSet(),
                    difficulty = options.difficulty
                )
            )
        }
        val batch = capacity.sheetBatch(options.difficulty)
        if (options.kind == SheetKind.Custom) {
            return writeCustomSheet(coast, options, count, nativeLanguage, wording, level, limits, batch, vocabulary, dealer, cardCount, provider, ai, onProgress)
        }
        val wanted = worksheetSentencesWanted(count, batch)
        // Fewer after a request that was too much for the model (see below).
        var perRequest = batch.sentences

        val written = ArrayList<WorksheetSentence>()
        var lastError: String? = null
        var requests = 0
        // Requests in a row that added nothing: the provider is down, or has run out of new sentences.
        var barren = 0
        var planned = worksheetRequests(count, batch)
        // Until the sheet is full, and through the planned requests for the sentences to spare, unless only a
        // few of those are left: a request for one or two would carry every card once more for them.
        fun moreNeeded() = written.size < count || (requests < planned && wanted - written.size > perRequest / 4)
        // Extra requests scale with sheet size so a 30+ sentence hard sheet whose batches average slightly fewer
        // unique lines than asked still finishes all requested sentences while progress continues.
        fun maxRequests() = planned + maxOf(EXTRA_REQUESTS, (planned + 1) / 2) * maxOf(1, (batch.sentences + perRequest - 1) / perRequest)
        while (moreNeeded() && requests < maxRequests() && barren < MAX_BARREN_REQUESTS) {
            requests++
            // After a request that was too much, none is made larger than the size that went through.
            val left = wanted - written.size
            val asked = if (perRequest < batch.sentences) minOf(perRequest, left) else worksheetAsk(left, perRequest)
            val dealSnapshot = dealer.snapshot()
            val (prompt, anchors, baseSeen) = withContext(background) {
                val deal = dealer.next(asked)
                val avoidSentences = (history.targetSentences.takeLast(limits.historyAvoid) + written.map { it.targetText })
                    .distinct()
                    .takeLast(limits.avoid)
                val builtPrompt = buildWorksheetPrompt(
                    cards = deal.cards,
                    targetLanguage = coast.language,
                    nativeLanguage = nativeLanguage,
                    count = asked,
                    difficulty = options.difficulty,
                    region = coast.region,
                    // Recent sheet sentences plus this sheet's latest ones, so consecutive sheets stay fresh
                    // without flooding a new sheet with 60 old sentences or evicting current-sheet lines.
                    avoid = avoidSentences,
                    quotas = deal.quotas,
                    anchors = deal.anchors,
                    level = level,
                    wording = wording,
                    direction = options.direction,
                    wordHelp = limits.wordHelp,
                    goal = coast.goal
                )
                Triple(builtPrompt, deal.anchors, written.mapTo(HashSet()) { normalizedWords(it.targetText) })
            }
            var lastReported = minOf(written.size, count)
            fun reportPartial(partial: String) {
                val completeLines = partial.substringBeforeLast('\n', missingDelimiterValue = "")
                if (completeLines.isEmpty()) return
                val inBatch = parseWorksheetResponse(completeLines, anchors)
                    .count { normalizedWords(it.targetText) !in baseSeen }
                val nowWritten = minOf(written.size + inBatch, count)
                if (nowWritten > lastReported) {
                    lastReported = nowWritten
                    onProgress(nowWritten)
                }
            }
            // Turned away for now (the per-minute limit is used up, or the provider is overloaded): the same
            // request goes through after the wait it names.
            val reply = ai.ask(prompt, waitWhileBusy = true, onPartialText = ::reportPartial) { it }
            val text = reply.value
            if (text == null) {
                dealer.restore(dealSnapshot)
                lastError = reply.error
                // The sheet has its sentences: the request was for the ones to spare, which aren't worth another try.
                if (written.size >= count) break
                // The model ran out of time or of room over this many sentences (one that thinks first does,
                // over a batch of hard ones): half as many can go through, in more requests than planned. Half
                // of what was asked for, which is fewer than a batch on a short sheet and at the end of a long one.
                if (reply.isTooLarge && asked > SMALLEST_BATCH) {
                    perRequest = maxOf(SMALLEST_BATCH, minOf(perRequest, asked) / 2)
                    planned = requests + (wanted - written.size + perRequest - 1) / perRequest
                    continue
                }
                barren++
                // A provider that can't be reached for the first request won't answer the next one either.
                if (written.isEmpty()) break else continue
            }
            val fresh = withContext(background) {
                val seen = HashSet(baseSeen)
                parseWorksheetResponse(text, anchors)
                    .filter { seen.add(normalizedWords(it.targetText)) }
                    .map { it.copy(newWords = vocabulary.wordHelp(it.targetText, it.newWords, maxWords = options.difficulty.wordHelpLimit)) }
            }
            if (fresh.isEmpty() && written.isEmpty()) lastError = "${provider.label}'s answer couldn't be read. Try again."
            barren = if (fresh.isEmpty()) barren + 1 else 0
            written += fresh
            onProgress(minOf(written.size, count))
        }

        val sentences = withContext(background) {
            pickWorksheetSentences(
                candidates = written,
                vocabulary = vocabulary,
                known = known,
                count = count,
                difficulty = options.difficulty,
                cardAlternatives = alternativesByCard,
                direction = options.direction,
                expectsWordHelp = limits.wordHelp,
                recentSentences = history.targetSentences
            )
        }
        if (sentences.isEmpty()) return WorksheetResult.Failed(lastError ?: "${provider.label} sent no sentences. Try again.")
        settings.recordWorksheet(
            coastId = coastId,
            anchoredCards = sentences.mapNotNull { it.card?.historyKey() },
            targetSentences = sentences.map { WorksheetCardDealer.oneLine(it.targetText) }
        )
        return WorksheetResult.Ready(
            Worksheet(
                coastName = coast.displayName,
                targetLanguage = coast.language,
                nativeLanguage = nativeLanguage,
                options = options.copy(count = count),
                sentences = sentences,
                date = clock.todayIn(timeZone),
                providerLabel = provider.label,
                cardCount = cardCount,
                islandCount = dealer.islandCount
            )
        )
    }

    /**
     * A custom sheet, from the point on where it parts from a translation sheet: everything before (the cards,
     * the provider, the level, the prompt's limits) is the same for both. The dealer only picks which cards a
     * prompt shows as the words to reach for; its anchors are for translation sentences.
     */
    private suspend fun writeCustomSheet(
        coast: Coast,
        options: WorksheetOptions,
        count: Int,
        nativeLanguage: String,
        wording: Wording,
        level: Level?,
        limits: SheetPromptLimits,
        batch: WorksheetBatch,
        vocabulary: CoastVocabulary,
        dealer: WorksheetCardDealer,
        cardCount: Int,
        provider: TranslationProvider,
        ai: AiConnection.Ready,
        onProgress: (Int) -> Unit
    ): WorksheetResult {
        val request = CustomSheetRequest(
            brief = options.brief,
            forms = customSheetForms(options.forms),
            count = count,
            difficulty = options.difficulty,
            targetLanguage = coast.language,
            nativeLanguage = nativeLanguage,
            region = coast.region,
            level = level,
            wording = wording,
            goal = coast.goal,
            limits = limits,
            batch = batch,
            vocabulary = vocabulary,
            cardsFor = { dealer.next(it).cards }
        )
        val written = CustomSheetWriter(ai, background, random).write(request, onProgress)
        if (written.exercises.isEmpty()) return WorksheetResult.Failed(written.error ?: CUSTOM_SHEET_UNUSABLE)
        // Only a brief that gave exercises is worth offering again; one that failed would only be tapped to fail.
        settings.recordWorksheetBrief(coast.coastId, options.brief)
        return WorksheetResult.Ready(
            Worksheet(
                coastName = coast.displayName,
                targetLanguage = coast.language,
                nativeLanguage = nativeLanguage,
                options = options.copy(count = count),
                sentences = emptyList(),
                date = clock.todayIn(timeZone),
                providerLabel = provider.label,
                cardCount = cardCount,
                islandCount = dealer.islandCount,
                exercises = written.exercises
            )
        )
    }

    private companion object {
        /** How many requests more than a sheet needs are made when answers come back short. */
        const val EXTRA_REQUESTS = 2

        /** After this many requests in a row that added nothing, the sheet is made from what there is. */
        const val MAX_BARREN_REQUESTS = 2
    }
}
