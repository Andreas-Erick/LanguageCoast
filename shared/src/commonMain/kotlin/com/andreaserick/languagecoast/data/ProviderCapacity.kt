package com.andreaserick.languagecoast.data

/**
 * The most a model on the phone (Local AI) writes in one answer. It is what the [ProviderCapacity.OnPhone]
 * sizes follow from: about eight two-sided sentence lines, or five practice-sheet lines with their word help.
 * A larger limit makes every answer that much slower on a phone; change the sizes with it.
 */
const val LOCAL_AI_MAX_OUTPUT_TOKENS = 512

/**
 * How much a provider's models take on in one request and about how long they take, so every piece of work and
 * every time estimate on screen is sized in one place instead of asking which provider it is.
 *
 * @property runsOnPhone The model runs on the phone: it reads short prompts and writes short answers, slowly.
 * @property thinksFirst The models think before they answer (the Claude 5 models), over a batch for a minute or more.
 * @property islandSentencesPerPrompt Sentences of an island about the user that one prompt asks for at most.
 * @property secondsPerIsland About how long writing one such island takes.
 * @property secondsPerStarterBatch About how long translating one batch of starter sentences takes.
 * @property sheetPrompt How much of a coast a practice-sheet prompt carries.
 */
enum class ProviderCapacity(
    val runsOnPhone: Boolean,
    val thinksFirst: Boolean,
    val islandSentencesPerPrompt: Int,
    val secondsPerIsland: Int,
    val secondsPerStarterBatch: Double,
    val sheetPrompt: SheetPromptLimits
) {
    /** On-device translation: it translates one sentence at a time and writes nothing. */
    OnDevice(runsOnPhone = true, thinksFirst = false, islandSentencesPerPrompt = 0, secondsPerIsland = 0, secondsPerStarterBatch = 0.0, SheetPromptLimits.Short),

    /** A language model on the phone; see [LOCAL_AI_MAX_OUTPUT_TOKENS]. */
    OnPhone(runsOnPhone = true, thinksFirst = false, islandSentencesPerPrompt = 8, secondsPerIsland = 240, secondsPerStarterBatch = 30.0, SheetPromptLimits.Short),

    /** Cloud models that think before they answer. */
    Thinking(runsOnPhone = false, thinksFirst = true, islandSentencesPerPrompt = PERSONAL_ISLAND_SENTENCES, secondsPerIsland = 25, secondsPerStarterBatch = 3.0, SheetPromptLimits.Full),

    /** Cloud models that answer a few dozen sentence pairs in seconds. */
    Cloud(runsOnPhone = false, thinksFirst = false, islandSentencesPerPrompt = PERSONAL_ISLAND_SENTENCES, secondsPerIsland = 25, secondsPerStarterBatch = 3.0, SheetPromptLimits.Full);

    /**
     * How a practice sheet of [difficulty] is asked: a local model writes a handful at a time and is too slow for
     * spares. A model that thinks first is asked for a dozen: twice that in one request took a top model past the
     * time and the room a request has, and the sheet failed.
     */
    fun sheetBatch(difficulty: WorksheetDifficulty): WorksheetBatch = when (this) {
        OnDevice, OnPhone -> WorksheetBatch.Local
        Thinking -> if (difficulty == WorksheetDifficulty.Hard) WorksheetBatch.HardThinking else WorksheetBatch.Thinking
        Cloud -> if (difficulty == WorksheetDifficulty.Hard) WorksheetBatch.HardCloud else WorksheetBatch.Cloud
    }

    /** About how long translating [count] starter sentences takes: a model a batch at a time, the device one each. */
    fun starterSeconds(count: Int): Double =
        if (this == OnDevice) count * 0.4 else (count.toDouble() / StarterTranslator.BATCH_SIZE + 1) * secondsPerStarterBatch

    companion object {
        fun of(provider: TranslationProvider): ProviderCapacity = when (provider) {
            TranslationProvider.OnDevice -> OnDevice
            TranslationProvider.LocalAi -> OnPhone
            TranslationProvider.Claude -> Thinking
            TranslationProvider.Gemini, TranslationProvider.OpenAI, TranslationProvider.Groq,
            TranslationProvider.DeepSeek, TranslationProvider.OpenRouter -> Cloud
        }
    }
}

/**
 * How much of a coast a practice-sheet prompt carries.
 *
 * @property cards Cards a prompt shows at most; a larger coast is dealt out over the prompts.
 * @property cardsPerIsland Cards of one island in a prompt, while other islands wait for room.
 * @property characters Characters of card text at most (about 100 a card), so long imported cards can't make a
 *     prompt many times the size of an ordinary one.
 * @property avoid Sentences already written that a request lists, so they aren't written again.
 * @property historyAvoid Of those, sentences from earlier sheets.
 * @property wordHelp Whether the model also explains the words a sentence needs that are on no card. A small model
 *     keeps to three fields better than to four.
 */
data class SheetPromptLimits(
    val cards: Int,
    val cardsPerIsland: Int,
    val characters: Int,
    val avoid: Int,
    val historyAvoid: Int,
    val wordHelp: Boolean
) {
    companion object {
        val Full = SheetPromptLimits(cards = 400, cardsPerIsland = 40, characters = 40_000, avoid = 60, historyAvoid = 15, wordHelp = true)

        /** A model on the phone must read a short prompt: fewer cards, of fewer islands, and less text. */
        val Short = SheetPromptLimits(cards = 60, cardsPerIsland = 20, characters = 4_000, avoid = 15, historyAvoid = 5, wordHelp = false)
    }
}
