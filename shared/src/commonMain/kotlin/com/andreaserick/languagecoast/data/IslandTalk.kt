package com.andreaserick.languagecoast.data

/**
 * An island's sentences joined into one short talk the user can rehearse aloud: what a language island is
 * for in the end. Single cards train recall; saying a minute about your family in one go trains speaking.
 *
 * @property targetText The talk in the coast's language.
 * @property nativeText What it means, in the user's language.
 */
data class IslandTalk(val targetText: String, val nativeText: String)

/** Cards a talk is made from at most; more would make it longer than a minute. */
const val TALK_CARD_LIMIT = 30

/** An island needs at least this many cards before a talk is worth making from it. */
const val TALK_MIN_CARDS = 5

/**
 * The prompt that has a model join [sentences] (native text to translation, the cards of the island named
 * [islandName]) into one talk in [targetLanguage] (as spoken in [region], if given), with its meaning in
 * [nativeLanguage]. The talk may only say what the cards say.
 */
fun buildTalkPrompt(
    islandName: String,
    sentences: List<Pair<String, String>>,
    nativeLanguage: String,
    targetLanguage: String,
    region: String?,
    wording: Wording = Wording.Unspecified
): String {
    val target = languageIn(targetLanguage, region)
    val lines = sentences.joinToString("\n") { (native, translation) -> "- $translation ($native)" }
    val speaker = speakerRule(wording, targetLanguage).let { if (it.isEmpty()) "" else "\n$it" }
    return buildString {
        append("A learner of $target has memorized the sentences below, from their flashcards on the topic \"$islandName\". Their native language is $nativeLanguage.\n\n")
        append("Join them into one short talk the learner can give aloud when someone says \"Tell me about that\": about 80 to 130 words of $targetLanguage that flow like speech.\n\n")
        append("Rules:\n")
        append("- Use the learner's sentences, as they are or lightly adapted, in an order that makes sense. Leave out questions to the other person, except one at the very end if it fits.\n")
        append("- Say nothing the sentences don't say: no new facts, names, numbers or opinions.\n")
        append("- Join them with simple connectors (and, but, because, then, also) and keep the grammar and vocabulary at the level of the sentences, so the learner can say it all.\n")
        append("- First person, natural spoken $targetLanguage, a few short paragraphs at most.$speaker\n\n")
        append("Sentences:\n$lines\n\n")
        append("Reply in exactly this format and nothing else:\n")
        append("TALK:\n[the talk in $targetLanguage]\nMEANING:\n[the same talk in $nativeLanguage]")
    }
}

/** Parses a reply to [buildTalkPrompt]; null when it doesn't have both parts. */
fun parseTalk(text: String): IslandTalk? {
    val cleaned = text.replace("```", "").replace("**", "")
    val talkAt = cleaned.indexOf("TALK:", ignoreCase = true)
    val meaningAt = cleaned.indexOf("MEANING:", ignoreCase = true)
    if (talkAt < 0 || meaningAt < talkAt) return null
    val talk = unbracketed(cleaned.substring(talkAt + "TALK:".length, meaningAt).trim())
    val meaning = unbracketed(cleaned.substring(meaningAt + "MEANING:".length).trim())
    return if (talk.isEmpty() || meaning.isEmpty()) null else IslandTalk(talk, meaning)
}
