package com.andreaserick.languagecoast.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Said when replies came back but none of their exercises could go on the sheet. */
const val CUSTOM_SHEET_UNUSABLE = "Couldn't make exercises from that brief. Try saying which grammar to practise."

/**
 * What one custom sheet asks for, as [WorksheetGenerator] worked it out from the coast and the options.
 *
 * @property cardsFor The cards the next prompt shows as the words to reach for, for a request of that many exercises.
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
    val cardsFor: (Int) -> List<WorksheetCard>
)

/**
 * Writes a custom sheet's exercises the way a translation sheet's sentences are written: a batch per
 * request with a few to spare, each request told what is on the sheet already, a request that was too
 * much made again with half as many, a busy provider waited for, and a model that keeps sending nothing
 * new given up on. What differs is the prompt, the reply and the pick ([pickExercises]); there are no
 * anchor cards, so nothing is recorded for the next sheet.
 */
internal class CustomSheetWriter(private val ai: AiConnection.Ready, private val background: CoroutineDispatcher) {

    /** The sheet's exercises, or none and why. */
    class Written(val exercises: List<Exercise>, val error: String?)

    suspend fun write(request: CustomSheetRequest, onProgress: (Int) -> Unit): Written {
        val count = request.count
        val batch = request.batch
        val wanted = worksheetSentencesWanted(count, batch)
        var perRequest = batch.sentences
        val written = ArrayList<Exercise>()
        var lastError: String? = null
        var anyReply = false
        var requests = 0
        var barren = 0
        var planned = worksheetRequests(count, batch)
        fun moreNeeded() = written.size < count || (requests < planned && wanted - written.size > perRequest / 4)
        fun maxRequests() = planned + maxOf(EXTRA_REQUESTS, (planned + 1) / 2) * maxOf(1, (batch.sentences + perRequest - 1) / perRequest)
        while (moreNeeded() && requests < maxRequests() && barren < MAX_BARREN_REQUESTS) {
            requests++
            val left = wanted - written.size
            val asked = if (perRequest < batch.sentences) minOf(perRequest, left) else worksheetAsk(left, perRequest)
            val seen = written.mapTo(HashSet()) { normalizedWords(it.task) }
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
                val inBatch = parseCustomSheetResponse(completeLines).count { it.form in request.forms && normalizedWords(it.task) !in seen }
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
                if (written.size >= count) break
                if (reply.isTooLarge && asked > SMALLEST_BATCH) {
                    perRequest = maxOf(SMALLEST_BATCH, minOf(perRequest, asked) / 2)
                    planned = requests + (wanted - written.size + perRequest - 1) / perRequest
                    continue
                }
                barren++
                if (written.isEmpty()) break else continue
            }
            anyReply = true
            val fresh = withContext(background) {
                parseCustomSheetResponse(text)
                    .filter { it.form in request.forms && seen.add(normalizedWords(it.task)) }
                    .map { it.copy(wordHelp = request.vocabulary.wordHelp(it.task, it.wordHelp)) }
            }
            barren = if (fresh.isEmpty()) barren + 1 else 0
            written += fresh
            onProgress(minOf(written.size, count))
        }
        val exercises = pickExercises(written, request.forms, count)
        // Replies that came back with nothing usable say so; a provider that never answered keeps its own reason.
        return Written(exercises, if (exercises.isEmpty()) (if (anyReply) CUSTOM_SHEET_UNUSABLE else lastError ?: CUSTOM_SHEET_UNUSABLE) else null)
    }

    private companion object {
        /** How many requests more than a sheet needs are made when answers come back short, as for a translation sheet. */
        const val EXTRA_REQUESTS = 2

        /** After this many requests in a row that added nothing, the sheet is made from what there is. */
        const val MAX_BARREN_REQUESTS = 2
    }
}
