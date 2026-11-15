package com.andreaserick.languagecoast.data

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorksheetTest {

    private val cards = listOf(
        WorksheetCard("Travel", "Wo ist der Bahnhof?", "Where is the station?"),
        WorksheetCard("Travel", "Ich gehe zum Strand.", "I am going to the beach."),
        WorksheetCard("Food", "Das Kind isst einen Apfel.", "The child is eating an apple.")
    )

    /** A card of a third island, and the cards a prompt's sentences are built from: one per numbered line. */
    private val bill = WorksheetCard("Restaurant", "Die Rechnung, bitte.", "The bill, please.")
    private val anchors = listOf(WorksheetAnchor(bill), WorksheetAnchor(cards[0]), WorksheetAnchor(cards[2]))

    /** The last field of a line of the answer, when the sheet prints word help. */
    private val newWordsField =
        "[the German words that are not on the flashcards, each with its English meaning, as \"word = meaning; word = meaning\", or - if there are none]"

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

    /** The prompt that asks for one sentence from each of [anchors], as a sheet's request does. */
    private fun anchored(
        difficulty: WorksheetDifficulty = WorksheetDifficulty.Medium,
        level: Level? = null,
        wording: Wording = Wording.Unspecified,
        direction: WorksheetDirection = WorksheetDirection.ToTarget,
        wordHelp: Boolean = true,
        avoid: List<String> = emptyList()
    ) = buildWorksheetPrompt(
        cards + bill, "German", "English", count = anchors.size, difficulty = difficulty, avoid = avoid,
        anchors = anchors, level = level, wording = wording, direction = direction, wordHelp = wordHelp
    )

    /** What [prompt] says matters, line by line: the numbered points, then the notes that start with a hyphen. */
    private fun whatMatters(prompt: String): List<String> =
        prompt.substringAfter("What matters, most important first:\n").substringBefore("\n\n").lines()

    /** The [count] sentences of [candidates] a sheet of [difficulty] keeps, on a coast with [cards]. */
    private fun kept(candidates: List<WorksheetSentence>, count: Int, difficulty: WorksheetDifficulty?): List<WorksheetSentence> {
        val known = cards.map { it.targetText }
        return pickWorksheetSentences(candidates, CoastVocabulary(known), known, count, difficulty)
    }

    @Test
    fun promptShowsTheCardsByIslandAndNamesTheCardEachSentenceIsBuiltFrom() {
        val prompt = anchored()

        assertTrue(prompt.startsWith("You are a native German speaker who teaches German to adults."))
        assertTrue("[Travel]\nWo ist der Bahnhof? = Where is the station?\nIch gehe zum Strand. = I am going to the beach." in prompt)
        assertTrue("[Food]\nDas Kind isst einen Apfel. = The child is eating an apple." in prompt)
        assertTrue("[Restaurant]\nDie Rechnung, bitte. = The bill, please." in prompt)
        // One sentence per numbered line, and a line is a topic with one card's side in the coast's language.
        assertTrue("Write one new German sentence for each numbered line below, with its English translation." in prompt)
        assertTrue("The sentence must use that flashcard's key word or phrase, in whatever form the sentence needs, and say something new with it" in prompt)
        assertTrue("\n\n1. Restaurant: Die Rechnung, bitte.\n2. Travel: Wo ist der Bahnhof?\n3. Food: Das Kind isst einen Apfel.\n\n" in prompt)
        assertTrue(
            prompt.endsWith("Answer with exactly one line per number and nothing else, in this format:\n[number] || [German sentence] || [English translation] || $newWordsField")
        )
        // The raw strings' indentation must not reach the model.
        assertTrue(prompt.lines().none { it.startsWith(" ") })
    }

    @Test
    fun whatMattersComesMostImportantFirstAndTheLearnersWordsOnlyFourth() {
        val points = whatMatters(anchored())

        assertTrue(points[0].startsWith("1. The German sentence is correct and natural: what a native speaker would really say"))
        assertTrue(points[1].startsWith("2. The English line says the same thing"))
        // The difficulty's length, then the level's words and grammar: two things, said apart.
        assertEquals("3. It is an everyday sentence of 5 to 10 words. Its words and grammar suit the learner, whose level the flashcards show.", points[2])
        // The cards' words are preferred, not required: a sentence takes the words it needs to be natural.
        assertTrue(points[3].startsWith("4. It leans on what the learner knows. Prefer words and patterns from the flashcards. Other words are welcome where the sentence needs them"))
        assertEquals("5. No two sentences are the same or differ by a single word, and none repeats a flashcard.", points[4])
    }

    @Test
    fun theDifficultySetsTheLengthAndWhatToDoWithACard() {
        val easy = anchored(WorksheetDifficulty.Easy)
        assertTrue(whatMatters(easy)[2].startsWith("3. It is short and simple: 3 to 7 words in one clause. "))
        // An easy sheet only says something new with the card.
        assertTrue("something that really is said in that topic's situation.\n\n1. Restaurant: " in easy)

        val medium = anchored(WorksheetDifficulty.Medium)
        assertTrue(whatMatters(medium)[2].startsWith("3. It is an everyday sentence of 5 to 10 words. "))
        assertTrue("ask a question with it, say what is not the case, say it about someone else (he, she, we or they), or add a time or a place" in medium)
        // The model picks the change, card by card: one it is handed can be the one that card takes badly.
        assertTrue("For each line pick the change that gives the most natural sentence, and vary the changes over the exercise." in medium)

        val hard = anchored(WorksheetDifficulty.Hard)
        assertTrue(
            whatMatters(hard)[2].startsWith(
                "3. It is a longer sentence of 9 to 16 words in two parts that belong together (a reason, a condition, a contrast or a second step), never two unrelated things joined. "
            )
        )
        assertTrue("Give each sentence a second part that belongs to the first: a reason (because), a condition (if, when), a contrast (but, although) or what happens next." in hard)
        assertFalse("ask a question with it" in hard)
    }

    @Test
    fun promptCarriesTheRegionAndWhatIsAlreadyWritten() {
        val prompt = buildWorksheetPrompt(
            cards + bill, "German", "English", count = 3, difficulty = WorksheetDifficulty.Hard, region = "Austria",
            avoid = listOf("Wo ist das Kind?", "Ich esse."), anchors = anchors
        )

        assertTrue(prompt.startsWith("You are a native German speaker from Austria who teaches German to adults."))
        assertTrue("what a native speaker from Austria would really say" in prompt)
        assertEquals("- Do not repeat any of these, which are already in the exercise: Wo ist das Kind? | Ich esse.", whatMatters(prompt).last())
        assertTrue(prompt.lines().none { it.startsWith(" ") })
        // A first request has nothing to avoid, and a region that is blank is none.
        assertTrue(whatMatters(anchored()).none { "Do not repeat any of these" in it })
        assertTrue(buildWorksheetPrompt(cards, "German", "English", 3, WorksheetDifficulty.Easy, region = " ").startsWith("You are a native German speaker who teaches"))
    }

    @Test
    fun promptSaysTheLevelOnlyWhenTheLearnerHasOne() {
        val reached = anchored(level = Level.A2)
        assertTrue("Their native language is English and their level is about A2.\n" in reached)
        assertEquals("3. It is an everyday sentence of 5 to 10 words. Its words and grammar suit level A2.", whatMatters(reached)[2])
        // A hard sheet for a beginner asks for a long sentence in a beginner's words; the two don't contradict each other.
        assertTrue(whatMatters(anchored(WorksheetDifficulty.Hard, level = Level.A1))[2].endsWith("never two unrelated things joined. Its words and grammar suit level A1."))

        val unknown = anchored()
        assertTrue("Their native language is English.\n" in unknown)
        assertFalse("level is about" in unknown)
        assertFalse("suit level" in unknown)
    }

    @Test
    fun promptSaysHowTheLearnerSpeaksOfThemselvesOnceTheyHaveSaid() {
        // The notes come after the numbered points.
        assertTrue(whatMatters(anchored(wording = Wording.Feminine))[5].startsWith("- The learner is a woman: wherever German marks the speaker's gender"))
        assertTrue(whatMatters(anchored(wording = Wording.Masculine))[5].startsWith("- The learner is a man: wherever German marks the speaker's gender"))
        assertTrue(whatMatters(anchored()).none { it.startsWith("- The learner") })
    }

    @Test
    fun aHintIsAskedForOnlyWhenTheLearnerTranslatesIntoTheCoastsLanguage() {
        val rule = "- Where the English line could fairly be translated in more than one way (formal or informal \"you\", one person or several), " +
            "end it with a short hint in round brackets, such as (formal) or (to a friend)."

        assertTrue(rule in whatMatters(anchored(direction = WorksheetDirection.ToTarget)))
        // Given the sentence in the coast's language, the learner sees which "you" it is.
        val reading = anchored(direction = WorksheetDirection.ToNative)
        assertFalse(rule in whatMatters(reading))
        assertFalse("hint" in reading)
    }

    @Test
    fun theNewWordsAreAskedForOnlyWhenTheSheetPrintsWordHelp() {
        val withHelp = anchored(wordHelp = true)
        assertTrue(whatMatters(withHelp)[3].endsWith("as long as they are common, everyday words; list them after the translation."))
        assertTrue(withHelp.endsWith("[number] || [German sentence] || [English translation] || $newWordsField"))

        // A small local model keeps to three fields better than to four.
        val without = anchored(wordHelp = false)
        assertTrue(whatMatters(without)[3].endsWith("as long as they are common, everyday words."))
        assertTrue(without.endsWith("Answer with exactly one line per number and nothing else, in this format:\n[number] || [German sentence] || [English translation]"))
        assertFalse("word = meaning" in without)
    }

    @Test
    fun aPromptWithoutAnchorsAsksForSentencesSpreadOverTheIslands() {
        val prompt = buildWorksheetPrompt(
            cards, "German", "English", count = 10, difficulty = WorksheetDifficulty.Medium,
            quotas = listOf(IslandQuota("Travel", 6), IslandQuota("Food", 4))
        )

        assertTrue("Write 10 new German sentences for the learner to translate, each with its English translation." in prompt)
        assertTrue("Each sentence takes a word or phrase from one topic's flashcards and says something new with it" in prompt)
        assertTrue("Of the sentences, write 6 about Travel and 4 about Food. Do something different with the word or phrase each time" in prompt)
        assertTrue("For each sentence pick the change that gives the most natural sentence" in prompt)
        assertFalse("numbered line" in prompt)
        // The same things matter in the same order, and a line of the answer has no number.
        assertEquals(whatMatters(anchored()), whatMatters(prompt))
        assertTrue(prompt.endsWith("Answer with exactly one line per sentence and nothing else, in this format:\n[German sentence] || [English translation] || $newWordsField"))
        assertTrue(prompt.lines().none { it.startsWith(" ") })
        assertTrue("Write 1 new German sentence for the learner" in buildWorksheetPrompt(cards, "German", "English", count = 1, difficulty = WorksheetDifficulty.Easy))
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
    fun theNumberOfALineTiesItsSentenceToTheCardItWasBuiltFrom() {
        // Out of order, and in every way a model writes the number.
        val reply = """
            3 || Isst das Kind keinen Apfel? || Isn't the child eating an apple? || keinen = no, not a; wirklich = really
            1. Wir möchten die Rechnung. || We would like the bill.
            [2] || [Der Bahnhof ist nicht weit.] || [The station is not far.] || -
            Ich gehe morgen zum Strand. || I am going to the beach tomorrow.
            9 || Wo ist der Strand? || Where is the beach?
        """.trimIndent()

        val sentences = parseWorksheetResponse(reply, anchors)

        assertEquals(
            listOf(
                WorksheetSentence(
                    "Isst das Kind keinen Apfel?", "Isn't the child eating an apple?", card = cards[2],
                    newWords = listOf(WordHelp("keinen", "no, not a"), WordHelp("wirklich", "really"))
                ),
                WorksheetSentence("Wir möchten die Rechnung.", "We would like the bill.", card = bill),
                WorksheetSentence("Der Bahnhof ist nicht weit.", "The station is not far.", card = cards[0]),
                // Without a number, or with one the prompt didn't have, it is a sentence of no card.
                WorksheetSentence("Ich gehe morgen zum Strand.", "I am going to the beach tomorrow."),
                WorksheetSentence("Wo ist der Strand?", "Where is the beach?")
            ),
            sentences
        )
        // A sentence practises the island of its card.
        assertEquals(listOf("Food", "Restaurant", "Travel", null, null), sentences.map { it.island })
        // A prompt that named no cards gets its numbers read all the same.
        assertEquals(listOf(WorksheetSentence("Wo ist der Strand?", "Where is the beach?")), parseWorksheetResponse("1 || Wo ist der Strand? || Where is the beach?"))
    }

    @Test
    fun aHintAtTheEndOfTheTranslationIsSplitOff() {
        val sentences = parseWorksheetResponse(
            """
            1 || Woher kommen Sie? || Where are you from? (formal)
            2 || Ich mag Tee (aber keinen Kaffee) sehr. || I like tea (but not coffee) a lot.
            3 || Wo ist das Kind? || Where is the child?
            4. || "Guten Abend, Anna!" || "Good evening, Anna!" / (to a friend)
            """.trimIndent()
        )

        // As a small model writes it: the sentence in quotes, the hint set off with a slash.
        assertEquals(WorksheetSentence("Guten Abend, Anna!", "Good evening, Anna!", hint = "to a friend"), sentences[3])
        assertEquals("Where are you from?" to "formal", sentences[0].nativeText to sentences[0].hint)
        // Brackets in the middle of a translation are part of it.
        assertEquals("I like tea (but not coffee) a lot." to null, sentences[1].nativeText to sentences[1].hint)
        assertEquals("Ich mag Tee (aber keinen Kaffee) sehr.", sentences[1].targetText)
        assertNull(sentences[2].hint)
    }

    @Test
    fun linesThatCannotBeASentenceWithItsTranslationAreDropped() {
        val reply = """
            [sentence in German] || [translation in English]
            [number] || [German sentence] || [English translation] || $newWordsField
            [German sentence] || [English translation]
            1 || German sentence || English translation
            1 || Wo ist der Bahnhof? || Wo ist der Bahnhof?
            2 || Wo ist der Bahnhof? = Where is the station? || Where is the station?
            3 || Das Kind isst. || The child = das Kind
            4 || Wo ist das Kind? || Where is the child?
            5 || wo ist das kind || Where's the child?
            6 || Taxi! || Taxi!
        """.trimIndent()

        // Left: the one real pair, without its repeat, and a word that is the same in both languages.
        assertEquals(
            listOf(WorksheetSentence("Wo ist das Kind?", "Where is the child?"), WorksheetSentence("Taxi!", "Taxi!")),
            parseWorksheetResponse(reply)
        )
        // A real sentence may end in the format line's words.
        assertEquals(
            listOf(WorksheetSentence("Wiederhole den Satz", "Repeat the sentence"), WorksheetSentence("Ich brauche eine Übersetzung", "I need a translation")),
            parseWorksheetResponse("1 || Wiederhole den Satz || Repeat the sentence\n2 || Ich brauche eine Übersetzung || I need a translation")
        )
    }

    @Test
    fun aBulletOrABracketAroundTheNumberIsStillTheNumber() {
        // A model that answers as a list, or dresses the number up.
        val reply = """
            - 3 || Isst das Kind keinen Apfel? || Isn't the child eating an apple? || -
            * 1. Wir möchten die Rechnung. || We would like the bill.
            (2) || Der Bahnhof ist nicht weit. || The station is not far. || -
            #1 || Die Rechnung stimmt nicht. || The bill is wrong.
            2: || Ist der Bahnhof weit? || Is the station far?
            - Ich gehe morgen zum Strand. || I am going to the beach tomorrow.
        """.trimIndent()

        val sentences = parseWorksheetResponse(reply, anchors)

        assertEquals(
            listOf(
                "Isst das Kind keinen Apfel?" to cards[2],
                "Wir möchten die Rechnung." to bill,
                "Der Bahnhof ist nicht weit." to cards[0],
                "Die Rechnung stimmt nicht." to bill,
                "Ist der Bahnhof weit?" to cards[0],
                "Ich gehe morgen zum Strand." to null
            ),
            sentences.map { it.targetText to it.card }
        )
        // A sentence that starts with a number is no numbered line.
        assertEquals("5 Kinder essen Äpfel.", parseWorksheetResponse("5 Kinder essen Äpfel. || 5 children are eating apples.").single().targetText)
    }

    @Test
    fun theFourthFieldIsReadAsWordHelp() {
        fun help(field: String) = parseWorksheetResponse("1 || Wir besuchen das Kind. || We are visiting the child. || $field").single().newWords

        assertEquals(listOf(WordHelp("besuchen", "to visit"), WordHelp("bar", "in cash")), help("besuchen = to visit; bar = in cash"))
        assertEquals(listOf(WordHelp("besuchen", "to visit")), help("[\"besuchen\" = \"to visit\"]"))
        assertEquals(emptyList(), help("-"))
        assertEquals(emptyList(), help("none"))
        // A sentence written into the field is no word.
        assertEquals(emptyList(), help("besuchen = this verb means to visit someone, and it is a regular one"))
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
    fun wordHelpIsKeptForTheWordsOfTheSentenceThatAreOnNoCard() {
        val vocabulary = CoastVocabulary(cards.map { it.targetText } + "Ich gehe nicht.")
        val help = listOf(
            WordHelp("liegen", "to lie, to be"),
            WordHelp("nicht", "not"),
            WordHelp("morgen", "tomorrow"),
            WordHelp("am", "at the")
        )

        // "liegen" is in the sentence as "liegt". The coast has "nicht", and "morgen" is not in the sentence.
        assertEquals(
            listOf(WordHelp("liegen", "to lie, to be"), WordHelp("am", "at the")),
            vocabulary.wordHelp("Der Bahnhof liegt nicht am Strand.", help)
        )
        assertEquals(emptyList(), vocabulary.wordHelp("Der Bahnhof liegt nicht am Strand.", emptyList()))
    }

    @Test
    fun wordHelpIsForAFewWordsAndForEachOnlyOnce() {
        val vocabulary = CoastVocabulary(cards.map { it.targetText })
        val help = listOf(
            WordHelp("Morgen", "tomorrow"),
            WordHelp("morgen", "in the morning"),
            WordHelp("regnen", "to rain"),
            WordHelp("es", "it"),
            WordHelp("leider", "unfortunately")
        )

        val kept = vocabulary.wordHelp("Morgen regnet es leider in Hamburg.", help)

        // More would give the exercise away.
        assertEquals(WORKSHEET_WORD_HELP, kept.size)
        assertEquals(listOf(WordHelp("Morgen", "tomorrow"), WordHelp("regnen", "to rain"), WordHelp("es", "it")), kept)
    }

    @Test
    fun wordHelpWorksInAScriptWithoutSpaces() {
        val vocabulary = CoastVocabulary(listOf("我喜欢茶", "你好"))
        val help = listOf(WordHelp("咖啡", "coffee"), WordHelp("茶", "tea"), WordHelp("牛奶", "milk"))

        // "茶" is on a card, and "牛奶" is not in the sentence.
        assertEquals(listOf(WordHelp("咖啡", "coffee")), vocabulary.wordHelp("我喜欢咖啡和茶", help))
    }

    @Test
    fun wordHelpFindsAKoreanNounUnderItsParticleAndAJapaneseVerbUnderItsEnding() {
        // Korean writes a noun and its particle as one word, and most nouns are two characters.
        val korean = CoastVocabulary(listOf("저는 집에 갑니다."))
        assertEquals(listOf(WordHelp("학교", "school")), korean.wordHelp("저는 학교에 갑니다.", listOf(WordHelp("학교", "school"), WordHelp("병원", "hospital"))))

        // A model lists a Japanese verb in its dictionary form; the sentence has another ending.
        val japanese = CoastVocabulary(listOf("電車が来ます。"))
        val help = listOf(WordHelp("遅れる", "to be late"), WordHelp("電話", "telephone"))
        // "電話" is not in the sentence, although its first character is.
        assertEquals(listOf(WordHelp("遅れる", "to be late")), japanese.wordHelp("電車が遅れています。", help))
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
    fun oneNewWordDoesNotCountAgainstASentence() {
        val known = cards.map { it.targetText }
        val vocabulary = CoastVocabulary(known)
        val twoNew = WorksheetSentence("Das Kind isst heute keinen Apfel.", "The child isn't eating an apple today.")
        val oneNew = WorksheetSentence("Das Kind isst heute einen Apfel.", "The child is eating an apple today.")
        val none = WorksheetSentence("Wo ist das Kind?", "Where is the child?")
        val alsoNone = WorksheetSentence("Das Kind geht zum Bahnhof.", "The child is going to the station.")

        assertTrue(vocabulary.keepsToCoast(none.targetText))
        assertTrue(vocabulary.keepsToCoast(oneNew.targetText))
        assertTrue(!vocabulary.keepsToCoast(twoNew.targetText))
        // The sentence that needed a new word is kept ahead of a later one that needed none.
        assertEquals(listOf(oneNew, none), pickWorksheetSentences(listOf(twoNew, oneNew, none, alsoNone), vocabulary, known, count = 2))
        assertEquals(listOf(oneNew, none, alsoNone), pickWorksheetSentences(listOf(twoNew, oneNew, none, alsoNone), vocabulary, known, count = 3))

        // Without spaces a new word is a character or two.
        val chinese = CoastVocabulary(listOf("我喜欢茶", "你好"))
        assertTrue(chinese.keepsToCoast("我喜欢咖啡"))
        assertTrue(!chinese.keepsToCoast("我喜欢咖啡和牛奶"))
    }

    @Test
    fun aHarderSheetsSentenceMayBringASecondNewWord() {
        val twoNew = WorksheetSentence("Das Kind isst heute keinen Apfel.", "The child isn't eating an apple today.")
        val none = WorksheetSentence("Wo ist das Kind?", "Where is the child?")

        assertEquals(listOf(none), kept(listOf(twoNew, none), count = 1, WorksheetDifficulty.Easy))
        assertEquals(listOf(twoNew), kept(listOf(twoNew, none), count = 1, WorksheetDifficulty.Medium))
        assertEquals(listOf(twoNew), kept(listOf(twoNew, none), count = 1, WorksheetDifficulty.Hard))
    }

    @Test
    fun aCopyOfACardIsTheFirstToGoThenASentenceWithSomethingAgainstIt() {
        val copy = WorksheetSentence("ich gehe zum Strand", "I am going to the beach.")
        val short = WorksheetSentence("Der Bahnhof!", "The station!")
        val fine = WorksheetSentence("Das Kind geht zum Bahnhof.", "The child is going to the station.")
        val candidates = listOf(copy, short, fine)

        // Two words are far too few for a medium sheet, but they are a sentence of their own; the copy practises nothing new.
        assertEquals(listOf(short, fine), kept(candidates, count = 2, WorksheetDifficulty.Medium))
        assertEquals(listOf(fine), kept(candidates, count = 1, WorksheetDifficulty.Medium))
        // With nothing to spare everything is kept, as it came.
        assertEquals(candidates, kept(candidates, count = 3, WorksheetDifficulty.Medium))
        assertEquals(candidates, kept(candidates, count = 10, WorksheetDifficulty.Medium))
    }

    @Test
    fun aSentenceFarTooLongOrTooShortForTheDifficultyGoesBeforeAWellSizedOne() {
        val five = WorksheetSentence("Das Kind geht zum Bahnhof.", "The child is going to the station.")
        val eight = WorksheetSentence("Ich gehe zum Strand, und das Kind isst.", "I am going to the beach, and the child is eating.")
        val ten = WorksheetSentence("Ich gehe zum Strand, und das Kind isst einen Apfel.", "I am going to the beach, and the child is eating an apple.")

        // Ten words are too many for an easy sheet (3 to 7), and five too few for a hard one (9 to 16).
        assertEquals(listOf(five), kept(listOf(ten, five), count = 1, WorksheetDifficulty.Easy))
        assertEquals(listOf(ten), kept(listOf(five, ten), count = 1, WorksheetDifficulty.Hard))
        // A word or two outside the range is no fault: the first one written stays.
        assertEquals(listOf(eight), kept(listOf(eight, five), count = 1, WorksheetDifficulty.Easy))
        // A medium sheet (5 to 10) takes both, and so does a pick that isn't told the difficulty.
        assertEquals(listOf(ten), kept(listOf(ten, five), count = 1, WorksheetDifficulty.Medium))
        assertEquals(listOf(ten), kept(listOf(ten, five), count = 1, difficulty = null))
    }

    @Test
    fun aQuestionOnOneSideOnlyCountsAgainstASentence() {
        val lopsided = WorksheetSentence("Geht das Kind zum Strand?", "The child is going to the beach.")
        val matched = WorksheetSentence("Wo ist der Strand?", "Where is the beach?")
        val statement = WorksheetSentence("Das Kind geht zum Bahnhof.", "The child is going to the station.")

        assertEquals(listOf(matched), kept(listOf(lopsided, matched), count = 1, WorksheetDifficulty.Medium))
        assertEquals(listOf(statement), kept(listOf(lopsided, statement), count = 1, WorksheetDifficulty.Medium))

        // Japanese ends a question without a question mark, so there the marks say nothing.
        val known = listOf("駅は遠いですね。", "どこですか。")
        val asks = WorksheetSentence("駅はどこですか。", "Where is the station?")
        val says = WorksheetSentence("駅は遠いです。", "The station is far.")
        assertEquals(listOf(asks), pickWorksheetSentences(listOf(asks, says), CoastVocabulary(known), known, count = 1, WorksheetDifficulty.Medium))

        // The same goes for the learner's own language: Greek ends a question with ";".
        val greek = WorksheetSentence("Wo ist der Strand?", "Πού είναι η παραλία;")
        assertEquals(listOf(greek), kept(listOf(greek, statement), count = 1, WorksheetDifficulty.Medium))
    }

    @Test
    fun aNearRepeatOfASentenceAlreadyTakenWaitsUntilTheOthersAreIn() {
        val beach = WorksheetSentence("Wo ist der Strand?", "Where is the beach?")
        val apple = WorksheetSentence("Wo ist der Apfel?", "Where is the apple?")
        val train = WorksheetSentence("Wo ist der Zug?", "Where is the train?")
        val other = WorksheetSentence("Das Kind geht zum Bahnhof.", "The child is going to the station.")

        // One pattern over and over teaches little: the later sentence is taken instead.
        assertEquals(listOf(beach, other), kept(listOf(beach, apple, other), count = 2, WorksheetDifficulty.Medium))
        // The sheet needs a third: the first near-repeat is in after all, and the second stays out.
        assertEquals(listOf(beach, apple, other), kept(listOf(beach, apple, train, other), count = 3, WorksheetDifficulty.Medium))
    }

    @Test
    fun aNearRepeatStillGoesAheadOfACopyOfACardAndOfASentenceWithSomethingAgainstIt() {
        val beach = WorksheetSentence("Wo ist der Strand?", "Where is the beach?")
        val apple = WorksheetSentence("Wo ist der Apfel?", "Where is the apple?")
        val copy = WorksheetSentence("ich gehe zum Strand", "I am going to the beach.")
        val stray = WorksheetSentence("Morgen regnet es in Hamburg.", "It will rain in Hamburg tomorrow.")
        val lopsided = WorksheetSentence("Geht das Kind zum Bahnhof?", "The child is going to the station.")

        // A near-repeat is a new sentence the learner can do; a card over again is not, and the others may be wrong or beyond them.
        assertEquals(listOf(beach, apple), kept(listOf(beach, apple, copy), count = 2, WorksheetDifficulty.Medium))
        assertEquals(listOf(beach, apple), kept(listOf(beach, apple, stray), count = 2, WorksheetDifficulty.Medium))
        assertEquals(listOf(beach, apple), kept(listOf(beach, apple, lopsided), count = 2, WorksheetDifficulty.Medium))
        // Of those with something against them, the copy is the last to be taken.
        assertEquals(listOf(beach, apple, lopsided), kept(listOf(beach, apple, copy, lopsided), count = 3, WorksheetDifficulty.Medium))
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
    fun aHintStaysWithTheLearnersLanguageSideWhicheverWayTheSheetGoes() {
        val formal = WorksheetSentence("Woher kommen Sie?", "Where are you from?", hint = "formal")

        // Into the coast's language: the hint says which of two translations is meant.
        val writing = sheet(WorksheetDirection.ToTarget)
        assertEquals("Where are you from? (formal)", writing.question(formal))
        assertEquals("Woher kommen Sie?", writing.answer(formal))
        // The other way no hint is asked for, so a bracket there is the model's own, perhaps part of the sentence
        // ("See you at 8 (in the evening)"): it stays with the translation, which is the answer.
        val reading = sheet(WorksheetDirection.ToNative)
        assertEquals("Woher kommen Sie?", reading.question(formal))
        assertEquals("Where are you from? (formal)", reading.answer(formal))
    }

    @Test
    fun wordHelpAndTheCardAreEachALineForTheSheet() {
        val sheet = sheet(WorksheetDirection.ToTarget)
        val built = WorksheetSentence(
            "Wir besuchen morgen das Kind.", "We are visiting the child tomorrow.", card = cards[2],
            newWords = listOf(WordHelp("besuchen", "to visit"), WordHelp("morgen", "tomorrow"))
        )
        assertEquals("besuchen = to visit · morgen = tomorrow", sheet.wordHelp(built))
        assertEquals("Your card: Das Kind isst einen Apfel. = The child is eating an apple.", sheet.cardLine(built))

        // A sentence that needed no new word, and came without a card, has neither line.
        val plain = WorksheetSentence("Wo ist das Kind?", "Where is the child?")
        assertNull(sheet.wordHelp(plain))
        assertNull(sheet.cardLine(plain))

        // A card of paragraphs, as an imported deck can have, is cut short under its answer.
        val long = plain.copy(card = WorksheetCard("Deck", "a".repeat(200), "kurz"))
        assertEquals("Your card: ${"a".repeat(160)}… = kurz", sheet.cardLine(long))
    }

    @Test
    fun theFirstPageSaysWhichIslandsTheSheetWasBuiltFrom() {
        fun builtFrom(vararg islands: String) = sheet(WorksheetDirection.ToTarget).copy(
            sentences = islands.mapIndexed { index, island -> WorksheetSentence("Satz $index", "Sentence $index", card = WorksheetCard(island, "Karte", "card")) },
            islandCount = 7
        )

        // The islands with the most sentences first.
        assertEquals("Built from your cards on Restaurant (2) and Travel (1).", builtFrom("Travel", "Restaurant", "Restaurant").focusLine)
        assertEquals(
            "Built from your cards on Restaurant (3), Travel (2) and Food (1).",
            builtFrom("Restaurant", "Travel", "Restaurant", "Food", "Travel", "Restaurant").focusLine
        )
        assertEquals("Built from your cards on Restaurant (2).", builtFrom("Restaurant", "Restaurant").focusLine)
        // Four islands by name, the others counted.
        assertEquals(
            "Built from your cards on Restaurant (2), Travel (1), Food (1), Home (1) and 1 more island.",
            builtFrom("Restaurant", "Restaurant", "Travel", "Food", "Home", "Work").focusLine
        )
        assertEquals(
            "Built from your cards on Restaurant (2), Travel (1), Food (1), Home (1) and 2 more islands.",
            builtFrom("Restaurant", "Restaurant", "Travel", "Food", "Home", "Work", "Family").focusLine
        )
        // A coast of one island has nothing to tell apart, and sentences that came without cards have nothing to name.
        assertNull(builtFrom("Restaurant", "Restaurant").copy(islandCount = 1).focusLine)
        assertNull(sheet(WorksheetDirection.ToTarget).focusLine)
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
