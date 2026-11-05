package com.andreaserick.languagecoast.data

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorksheetTest {

    private val cards = listOf(
        WorksheetCard("Travel", "Wo ist der Bahnhof?", "Where is the station?"),
        WorksheetCard("Travel", "Ich gehe zum Strand.", "I am going to the beach."),
        WorksheetCard("Food", "Das Kind isst einen Apfel.", "The child is eating an apple.")
    )

    private fun sheet(direction: WorksheetDirection) = Worksheet(
        coastName = "German Coast",
        targetLanguage = "German",
        nativeLanguage = "English",
        options = WorksheetOptions(direction = direction),
        sentences = listOf(WorksheetSentence("Wo ist das Kind?", "Where is the child?")),
        date = LocalDate(2026, 11, 5),
        providerLabel = "Gemini",
        cardCount = 3,
        islandCount = 2
    )

    @Test
    fun promptShowsTheCardsByIslandAndAllowsNoOtherWords() {
        val prompt = buildWorksheetPrompt(cards, "German", "English", count = 10, difficulty = WorksheetDifficulty.Medium)

        assertTrue("[Travel]\nWo ist der Bahnhof? = Where is the station?\nIch gehe zum Strand. = I am going to the beach." in prompt)
        assertTrue("[Food]\nDas Kind isst einen Apfel. = The child is eating an apple." in prompt)
        assertTrue("Write 10 new German sentences" in prompt)
        assertTrue("Use ONLY German words that are on the flashcards" in prompt)
        assertTrue("Level: medium" in prompt)
        assertTrue(prompt.endsWith("[sentence in German] || [translation in English]"))
        // The raw strings' indentation must not reach the model.
        assertTrue(prompt.lines().none { it.startsWith(" ") })
    }

    @Test
    fun promptCarriesTheLevelTheRegionAndWhatIsAlreadyWritten() {
        val prompt = buildWorksheetPrompt(
            cards, "German", "English", count = 1, difficulty = WorksheetDifficulty.Hard, region = "Austria", avoid = listOf("Wo ist das Kind?", "Ich esse.")
        )

        assertTrue("Write 1 new German sentence for" in prompt)
        assertTrue("Level: hard" in prompt)
        assertTrue("a native speaker from Austria would say" in prompt)
        assertTrue("6. Do not repeat any of these, which are already on the sheet: Wo ist das Kind? | Ich esse." in prompt)
        assertTrue(prompt.lines().none { it.startsWith(" ") })
        assertTrue("Level: easy" in buildWorksheetPrompt(cards, "German", "English", 3, WorksheetDifficulty.Easy))
    }

    @Test
    fun replyIsReadLineByLineWhateverItsWrapping() {
        val reply = """
            Here are your sentences:
            1. Wo ist das Kind? || Where is the child?
            2) [Ich gehe zum Bahnhof.] || [I am going to the station.]
            - "Das Kind geht zum Strand." || "The child is going to the beach."
            **Der Apfel ist am Strand.** || The apple is on the beach.
            wo ist das kind || Where's the child?
            Kein Trenner hier
            Nur eine Seite ||
        """.trimIndent()

        assertEquals(
            listOf(
                WorksheetSentence("Wo ist das Kind?", "Where is the child?"),
                WorksheetSentence("Ich gehe zum Bahnhof.", "I am going to the station."),
                WorksheetSentence("Das Kind geht zum Strand.", "The child is going to the beach."),
                WorksheetSentence("Der Apfel ist am Strand.", "The apple is on the beach.")
            ),
            parseWorksheetResponse(reply)
        )
        assertTrue(parseWorksheetResponse("I can't help with that.").isEmpty())
    }

    @Test
    fun vocabularyKnowsACardsWordsAndTheirOtherForms() {
        val vocabulary = CoastVocabulary(cards.map { it.targetText } + "búð")

        // Another ending, a longer form, a number and a different case all count as the coast's words.
        assertEquals(emptyList(), vocabulary.unknownWords("Wo ist der Bahnhof?"))
        assertEquals(emptyList(), vocabulary.unknownWords("3 Kinder gehen zum Strand: Bahnhöfe!"))
        assertEquals(emptyList(), vocabulary.unknownWords("búðina"))
        assertEquals(listOf("morgen", "regnet", "es"), vocabulary.unknownWords("Morgen regnet es."))
        // A short word doesn't vouch for a long one that merely starts like it.
        assertEquals(listOf("derzeit"), vocabulary.unknownWords("derzeit"))
        assertEquals(0.0, vocabulary.unknownShare("Wo ist das Kind?"))
        assertEquals(0.5, vocabulary.unknownShare("Wo regnet es Kind"))
        assertEquals(1.0, vocabulary.unknownShare("?!"))
    }

    @Test
    fun scriptsWithoutSpacesAreCountedByCharacter() {
        val vocabulary = CoastVocabulary(listOf("我喜欢茶", "你好"))

        assertEquals(emptyList(), vocabulary.unknownWords("你喜欢茶"))
        assertEquals(listOf("咖", "啡"), vocabulary.unknownWords("我喜欢咖啡"))
        assertEquals(0.4, vocabulary.unknownShare("我喜欢咖啡"))
    }

    @Test
    fun theSentencesClosestToTheCardsAreKeptInTheirOrder() {
        val known = cards.map { it.targetText }
        val vocabulary = CoastVocabulary(known)
        val stray = WorksheetSentence("Morgen regnet es in Hamburg.", "It will rain in Hamburg tomorrow.")
        val copy = WorksheetSentence("wo ist der Bahnhof", "Where is the station?")
        val close = WorksheetSentence("Wo ist das Kind?", "Where is the child?")
        val closer = WorksheetSentence("Das Kind geht zum Bahnhof.", "The child is going to the station.")
        val candidates = listOf(stray, copy, close, closer)

        assertEquals(listOf(close, closer), pickWorksheetSentences(candidates, vocabulary, known, count = 2))
        // A copy of a card is the last resort, after a sentence with words the coast doesn't have.
        assertEquals(listOf(stray, close, closer), pickWorksheetSentences(candidates, vocabulary, known, count = 3))
        assertEquals(candidates, pickWorksheetSentences(candidates, vocabulary, known, count = 10))
    }

    @Test
    fun theSheetAsksInOneLanguageAndAnswersInTheOther() {
        val writing = sheet(WorksheetDirection.ToTarget)
        assertEquals("English" to "German", writing.fromLanguage to writing.toLanguage)
        assertEquals("Where is the child?", writing.question(writing.sentences.single()))
        assertEquals("Wo ist das Kind?", writing.answer(writing.sentences.single()))

        val reading = sheet(WorksheetDirection.ToNative)
        assertEquals("German" to "English", reading.fromLanguage to reading.toLanguage)
        assertEquals("Wo ist das Kind?", reading.question(reading.sentences.single()))
        assertEquals("Where is the child?", reading.answer(reading.sentences.single()))
    }

    @Test
    fun theFileIsNamedAfterTheCoastAndTheDay() {
        assertEquals("german-coast-practice-2026-11-05.pdf", sheet(WorksheetDirection.ToTarget).fileName)
        assertEquals("norwegian-bokmål-coast-practice-2026-11-05.pdf", sheet(WorksheetDirection.ToTarget).copy(coastName = "Norwegian (Bokmål) Coast").fileName)
        assertEquals("coast-practice-2026-11-05.pdf", sheet(WorksheetDirection.ToTarget).copy(coastName = "??").fileName)
    }

    @Test
    fun aSheetStartsAsTenMediumSentences() {
        assertEquals(WorksheetOptions(count = 10, difficulty = WorksheetDifficulty.Medium, direction = WorksheetDirection.ToTarget), WorksheetOptions())
        assertTrue(WORKSHEET_DEFAULT_COUNT in WORKSHEET_COUNT_RANGE)
    }
}
