package com.andreaserick.languagecoast.data

/**
 * The first sentences of a new coast, translated ahead of time and shipped with the app, so that the very
 * first session can start at once: before the phone has downloaded its translation pack, and before an AI
 * provider is set up (on an iPhone there is no on-device translation, so without them the first session
 * would have nothing to offer).
 *
 * They are three of the [PhraseBank]'s greetings, chosen because they read the same for everyone: no
 * gender of the speaker, and polite enough for anyone. Portuguese is left out, since "thank you" there
 * depends on who says it. Both sides come from this table, so it only helps when the user's language is
 * in it too; otherwise the coast waits for translation as before.
 */
object StarterBundle {

    /** The sentences, in English as the bank has them, in the order they are offered. */
    val SENTENCES: List<String> = listOf("Hello, how are you?", "Thank you very much.", "Yes, please.")

    /** The island they go on, as in the [PhraseBank]. */
    const val CATEGORY = "Greetings"

    private const val EMOJI = "👋"

    /** [SENTENCES] in each language, by the language's English name (as in [Languages.ALL]). */
    internal val TRANSLATIONS: Map<String, List<String>> = mapOf(
        "English" to SENTENCES,
        "Spanish" to listOf("Hola, ¿qué tal?", "Muchas gracias.", "Sí, por favor."),
        "French" to listOf("Bonjour, comment allez-vous ?", "Merci beaucoup.", "Oui, s'il vous plaît."),
        "German" to listOf("Hallo, wie geht's?", "Vielen Dank.", "Ja, bitte."),
        "Italian" to listOf("Ciao, come stai?", "Grazie mille.", "Sì, per favore."),
        "Dutch" to listOf("Hallo, hoe gaat het?", "Dank je wel.", "Ja, graag."),
        "Swedish" to listOf("Hej, hur mår du?", "Tack så mycket.", "Ja, tack."),
        "Danish" to listOf("Hej, hvordan har du det?", "Mange tak.", "Ja tak."),
        "Norwegian" to listOf("Hei, hvordan har du det?", "Tusen takk.", "Ja takk."),
        "Finnish" to listOf("Hei, mitä kuuluu?", "Kiitos paljon.", "Kyllä, kiitos."),
        "Polish" to listOf("Cześć, jak się masz?", "Dziękuję bardzo.", "Tak, poproszę."),
        "Czech" to listOf("Ahoj, jak se máš?", "Moc děkuji.", "Ano, prosím."),
        "Russian" to listOf("Привет, как дела?", "Большое спасибо.", "Да, пожалуйста."),
        "Ukrainian" to listOf("Привіт, як справи?", "Щиро дякую.", "Так, будь ласка."),
        "Greek" to listOf("Γεια σου, τι κάνεις;", "Ευχαριστώ πολύ.", "Ναι, παρακαλώ."),
        "Turkish" to listOf("Merhaba, nasılsın?", "Çok teşekkür ederim.", "Evet, lütfen."),
        "Japanese" to listOf("こんにちは、お元気ですか？", "どうもありがとうございます。", "はい、お願いします。"),
        "Korean" to listOf("안녕하세요, 잘 지내세요?", "정말 감사합니다.", "네, 부탁합니다.")
    )

    /**
     * The shipped sentences for a coast in [targetLanguage] learned by someone who speaks [nativeLanguage],
     * or empty when either language isn't in the table (or they are the same language).
     */
    fun firstSentences(nativeLanguage: String, targetLanguage: String): List<ProposedSentence> {
        if (nativeLanguage == targetLanguage) return emptyList()
        val native = TRANSLATIONS[nativeLanguage] ?: return emptyList()
        val target = TRANSLATIONS[targetLanguage] ?: return emptyList()
        return native.zip(target) { front, back -> ProposedSentence(front, back, CATEGORY, EMOJI) }
    }
}
