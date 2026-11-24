package com.andreaserick.languagecoast.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** Said when replies came back but none of their exercises could go on the sheet. */
const val CUSTOM_SHEET_UNUSABLE = "Couldn't make exercises from that brief. Try saying which grammar to practise."

/**
 * What one custom sheet asks for, as [WorksheetGenerator] worked it out from the coast and the options.
 *
 * @property cardsFor The cards the next prompt shows as the words to reach for, for a request of that many exercises.
 * @property takeBackCards Undoes the last [cardsFor], for a request that got no answer: made again, it shows
 *   the same cards, rather than passing over islands whose turn it was.
 */
internal class CustomSheetRequest(
    val brief: String,
    val forms: List<ExerciseForm>,
    val count: Int,
    val difficulty: WorksheetDifficulty,
    val targetLanguage: String,
    val nativeLanguage: String,
    val region: String?,
    val level: Level?,
    val wording: Wording,
    val goal: String?,
    val limits: SheetPromptLimits,
    val batch: WorksheetBatch,
    val vocabulary: CoastVocabulary,
    val cardsFor: (Int) -> List<WorksheetCard>,
    val takeBackCards: () -> Unit = {}
)

/**
 * Writes a custom sheet's exercises the way a translation sheet's sentences are written: a batch per
 * request with a few to spare, each request told what is on the sheet already, a request that was too
 * much made again with half as many, a busy provider waited for, and a model that keeps sending nothing
 * new given up on. What differs is the prompt, the reply and the pick ([pickExercises]); there are no
 * anchor cards, so nothing is recorded for the next sheet. A multiple-choice exercise's options are put in
 * an order drawn from [random] ([shuffledOptions]), so a fixed seed gives a fixed sheet.
 */
internal class CustomSheetWriter(
    private val ai: AiConnection.Ready,
    private val background: CoroutineDispatcher,
    private val random: Random
) {

    /** The sheet's exercises, or none and why. */
    class Written(val exercises: List<Exercise>, val error: String?)

    suspend fun write(request: CustomSheetRequest, onProgress: (Int) -> Unit): Written {
        val count = request.count
        val batch = request.batch
        val wanted = worksheetSentencesWanted(count, batch)
        var perRequest = batch.sentences
        val written = ArrayList<Exercise>()
        var lastError: String? = null
        // Whether the last request got no answer; true until one does, so a provider that never answered keeps its reason.
        var lastFailed = true
        var requests = 0
        var barren = 0
        var planned = worksheetRequests(count, batch)
        fun moreNeeded() = written.size < count || (requests < planned && wanted - written.size > perRequest / 4)
        fun maxRequests() = planned + maxOf(EXTRA_REQUESTS, (planned + 1) / 2) * maxOf(1, (batch.sentences + perRequest - 1) / perRequest)
        while (moreNeeded() && requests < maxRequests() && barren < MAX_BARREN_REQUESTS) {
            requests++
            val left = wanted - written.size
            val asked = if (perRequest < batch.sentences) minOf(perRequest, left) else worksheetAsk(left, perRequest)
            val seen = written.mapTo(HashSet(), ::exerciseKey)
            val prompt = withContext(background) {
                buildCustomSheetPrompt(
                    brief = request.brief,
                    forms = request.forms,
                    cards = request.cardsFor(asked),
                    targetLanguage = request.targetLanguage,
                    nativeLanguage = request.nativeLanguage,
                    count = asked,
                    difficulty = request.difficulty,
                    region = request.region,
                    avoid = written.map { WorksheetCardDealer.oneLine(it.task) }.takeLast(request.limits.avoid),
                    level = request.level,
                    wording = request.wording,
                    wordHelp = request.limits.wordHelp,
                    goal = request.goal
                )
            }
            var lastReported = minOf(written.size, count)
            fun reportPartial(partial: String) {
                val completeLines = partial.substringBeforeLast('\n', missingDelimiterValue = "")
                if (completeLines.isEmpty()) return
                val inBatch = parseCustomSheetResponse(completeLines).count { it.form in request.forms && exerciseKey(it) !in seen }
                val nowWritten = minOf(written.size + inBatch, count)
                if (nowWritten > lastReported) {
                    lastReported = nowWritten
                    onProgress(nowWritten)
                }
            }
            val reply = ai.ask(prompt, waitWhileBusy = true, onPartialText = ::reportPartial) { it }
            val text = reply.value
            if (text == null) {
                lastError = reply.error
                lastFailed = true
                request.takeBackCards()
                if (written.size >= count) break
                if (reply.isTooLarge && asked > SMALLEST_BATCH) {
                    perRequest = maxOf(SMALLEST_BATCH, minOf(perRequest, asked) / 2)
                    planned = requests + (wanted - written.size + perRequest - 1) / perRequest
                    continue
                }
                barren++
                if (written.isEmpty()) break else continue
            }
            lastFailed = false
            val fresh = withContext(background) {
                parseCustomSheetResponse(text)
                    .filter { it.form in request.forms && seen.add(exerciseKey(it)) }
                    .map { shuffledOptions(it.copy(wordHelp = request.vocabulary.wordHelp(it.task, it.wordHelp)), random) }
            }
            barren = if (fresh.isEmpty()) barren + 1 else 0
            written += fresh
            onProgress(minOf(written.size, count))
        }
        val exercises = pickExercises(written, request.forms, count)
        // Replies that came back with nothing usable say so. When the last request got no answer, its reason
        // (no connection, no credit) is what to fix, even if an earlier reply was unusable.
        return Written(exercises, if (exercises.isEmpty()) (if (lastFailed) lastError else null) ?: CUSTOM_SHEET_UNUSABLE else null)
    }

    private companion object {
        /** How many requests more than a sheet needs are made when answers come back short, as for a translation sheet. */
        const val EXTRA_REQUESTS = 2

        /** After this many requests in a row that added nothing, the sheet is made from what there is. */
        const val MAX_BARREN_REQUESTS = 2
    }
}
