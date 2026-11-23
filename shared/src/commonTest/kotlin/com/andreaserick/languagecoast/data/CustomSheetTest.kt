package com.andreaserick.languagecoast.data

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomSheetTest {

    private val cards = listOf(
        WorksheetCard("Animals", "der Hund", "the dog"),
        WorksheetCard("Animals", "die Katze", "the cat"),
        WorksheetCard("Food", "Ich kaufe einen Apfel.", "I am buying an apple.")
    )

    private fun prompt(
        brief: String = "Plural from the singular",
        targetLanguage: String = "German",
        region: String? = null,
        level: Level? = null,
        wording: Wording = Wording.Unspecified,
        wordHelp: Boolean = true,
        avoid: List<String> = emptyList()
    ) = buildCustomSheetPrompt(
        brief = brief, forms = listOf(ExerciseForm.ShortAnswer), cards = cards, targetLanguage = targetLanguage,
        nativeLanguage = "English", count = 4, difficulty = WorksheetDifficulty.Medium, region = region, avoid = avoid,
        level = level, wording = wording, wordHelp = wordHelp
    )

    @Test
    fun theCustomPromptCarriesTheBriefTheCardsAndWhichLanguageGoesWhere() {
        val prompt = prompt(region = "Austria", level = Level.A2, wording = Wording.Feminine, avoid = listOf("die Maus"))
        assertTrue("\"Plural from the singular\"" in prompt)
        assertTrue("der Hund = the dog\ndie Katze = the cat\nIch kaufe einen Apfel. = I am buying an apple." in prompt)
        assertTrue("Write 4 exercises" in prompt)
        // Instructions in the learner's language, everything they work on in the coast's.
        assertTrue("The instruction is in English" in prompt)
        assertTrue("The task, its hints and the answer are in German" in prompt)
        assertTrue("from Austria" in prompt)
        assertTrue("level A2" in prompt)
        assertTrue(speakerRule(Wording.Feminine, "German") in prompt)
        assertTrue("die Maus" in prompt.substringAfter("Do not repeat"))
        // Words to drill come from the cards first.
        assertTrue("take it from the flashcards first" in prompt)
    }

    @Test
    fun correctnessAndASingleRightAnswerComeBeforeTheLearnersWords() {
        val prompt = prompt()
        val rules = prompt.substringAfter("What matters, most important first:\n").lines()
        assertTrue(rules[0].startsWith("1. ") && "correct" in rules[0])
        assertTrue(rules[1].startsWith("2. ") && "single right answer" in rules[1] && "never visible in the task" in rules[1])
        assertTrue(rules[3].startsWith("4. ") && "flashcards" in rules[3])
    }

    @Test
    fun norwegianIsWrittenInBokmalUnlessTheCoastsRegionSaysNynorsk() {
        assertTrue("native Norwegian Bokmål speaker" in prompt(targetLanguage = "Norwegian"))
        assertTrue("are in Norwegian Bokmål" in prompt(targetLanguage = "Norwegian", region = "Oslo"))
        val nynorsk = prompt(targetLanguage = "Norwegian", region = "Vestland, nynorsk")
        assertTrue("native Norwegian Nynorsk speaker" in nynorsk)
        assertFalse("Bokmål" in nynorsk)
        assertFalse("Bokmål" in prompt(targetLanguage = "Swedish"))
    }

    @Test
    fun aShortAnswerSheetAsksForOneLinePerExerciseWithWordHelpOnlyWhereItIsPrinted() {
        val format = prompt().substringAfter("in this format:\n")
        assertEquals("short || [instruction in English] || [task in German] || [answer in German] || " +
            "[the German words of the task that are not on the flashcards, each with its English meaning, as \"word = meaning; word = meaning\", or - if there are none]", format)
        assertEquals("short || [instruction in English] || [task in German] || [answer in German]", prompt(wordHelp = false).substringAfter("in this format:\n"))
    }

    @Test
    fun aSheetThatMayHaveMultipleChoiceSaysWhatAGoodOneIsAndAsksForItsThreeOptions() {
        val prompt = buildCustomSheetPrompt(
            brief = "Dative articles", forms = customSheetForms(emptySet()), cards = cards, targetLanguage = "German",
            nativeLanguage = "English", count = 4, difficulty = WorksheetDifficulty.Medium
        )
        // Left to the model, a sheet may have multiple choice among its forms.
        assertTrue("\"choice\"" in prompt.substringAfter("Each exercise is one of these forms:").substringBefore("\n"))
        val rule = prompt.lines().single { it.startsWith("- A \"choice\" exercise") }
        assertTrue("three options" in rule && "exactly one of them is right" in rule && "not in the task" in rule, rule)
        assertTrue("|| [the three options, as \"a) option; b) option; c) option\", or - if it is not multiple choice]" in prompt.substringAfter("in this format:\n"))

        // A sheet of short answers only isn't told about options at all.
        assertFalse("option" in prompt(), "A short-answer sheet mentions options")
    }

    @Test
    fun aBriefIsQuotedOnOneLine() {
        val prompt = prompt(brief = "  Nominative →\ndative  ")
        assertTrue("\"Nominative → dative\"" in prompt)
    }

    private fun customSheet(exercises: List<Exercise>, brief: String = "Plural from the singular") = Worksheet(
        coastName = "German Coast",
        targetLanguage = "German",
        nativeLanguage = "English",
        options = WorksheetOptions(kind = SheetKind.Custom, brief = brief),
        sentences = emptyList(),
        date = LocalDate(2026, 11, 5),
        providerLabel = "Gemini",
        cardCount = 6,
        islandCount = 2,
        exercises = exercises
    )

    private fun short(task: String, answer: String, instruction: String? = "Write the plural.") =
        Exercise(ExerciseForm.ShortAnswer, instruction, task, answer)

    @Test
    fun aSheetIsATranslationSheetUnlessTheLearnerPicksCustom() {
        val options = WorksheetOptions()
        assertEquals(SheetKind.Translation, options.kind)
        assertEquals("", options.brief)
        assertEquals(emptySet(), options.forms)
    }

    @Test
    fun aCustomSheetsFileSaysItIsACustomSheet() {
        val sheet = customSheet(listOf(short("der Hund", "die Hunde")))
        assertEquals("german-coast-custom-practice-2026-11-05.pdf", sheet.fileName)
        assertEquals(1, sheet.exerciseCount)
        // Exercises come without cards, so the first page names no islands.
        assertNull(sheet.focusLine)
    }

    @Test
    fun aShortAnswerLineIsReadAsItsInstructionTaskAnswerAndWordHelp() {
        val read = parseCustomSheetResponse(
            "short || Write the plural. || der Hund || die Hunde || -\n" +
                "short || Write the plural. || die Maus || die Mäuse || Maus = mouse\n" +
                "short || - || das Haus || die Häuser"
        )
        assertEquals(
            listOf(
                short("der Hund", "die Hunde"),
                short("die Maus", "die Mäuse").copy(wordHelp = listOf(WordHelp("Maus", "mouse"))),
                short("das Haus", "die Häuser", instruction = null)
            ),
            read
        )
    }

    private fun choice(task: String, answer: String, vararg options: String) =
        Exercise(ExerciseForm.MultipleChoice, "Pick the right article.", task, answer, options = options.toList())

    @Test
    fun aMultipleChoiceLineIsReadAsItsThreeOptionsWithTheAnswerSpelledOutAndNoWordHelp() {
        val read = parseCustomSheetResponse(
            """
            choice || Pick the right article. || Ich fahre mit ___ Zug. || dem || a) den; b) dem; c) der
            choice || Pick the right article. || Ich gebe ___ Frau das Buch. || b) der || a) die; b) der; c) den || Buch = book
            multiple choice || Pick the right article. || Wir helfen ___ Kind. || c || a) das b) den c) dem
            choice || Pick the right article. || Er wohnt bei ___ Eltern. || seinen || a) seine || b) seinen || c) seiner
            choice || Pick the right article. || Sie kommt aus ___ Schweiz. || der || die / der / den
            """.trimIndent()
        )
        assertEquals(
            listOf(
                choice("Ich fahre mit ___ Zug.", "dem", "den", "dem", "der"),
                choice("Ich gebe ___ Frau das Buch.", "der", "die", "der", "den"),
                choice("Wir helfen ___ Kind.", "dem", "das", "den", "dem"),
                choice("Er wohnt bei ___ Eltern.", "seinen", "seine", "seinen", "seiner"),
                choice("Sie kommt aus ___ Schweiz.", "der", "die", "der", "den")
            ),
            read
        )
    }

    @Test
    fun anAnswerThatIsAlsoAnOptionIsThatOptionEvenWhenItLooksLikeALetter() {
        // Italian "a" is a word: when the options have it, the answer means that option, not the first one.
        val read = parseCustomSheetResponse("choice || Pick the preposition. || Vado ___ Roma. || a || a) in; b) a; c) da").single()
        assertEquals("a", read.answer)
        assertEquals(listOf("in", "a", "da"), read.options)
    }

    @Test
    fun aMultipleChoiceExerciseWithoutThreeDifferentOptionsOrWithoutItsAnswerAmongThemIsDropped() {
        val read = parseCustomSheetResponse(
            """
            choice || Pick. || Ich fahre mit ___ Zug. || dem || a) den; b) dem
            choice || Pick. || Ich fahre mit ___ Bus. || dem || a) den; b) dem; c) der; d) des
            choice || Pick. || Ich fahre mit ___ Auto. || dem || a) dem; b) Dem; c) der
            choice || Pick. || Ich gehe in ___ Park. || den || a) dem; b) der; c) des
            choice || Pick. || Ich gehe in ___ Stadt. || d || a) dem; b) der; c) die
            choice || Pick. || Ich gehe in ___ Kino. || das || -
            choice || Pick. || Ich gehe in ___ Garten. || den
            choice || Pick. || Ich sitze in ___ Garten. || dem || a) dem; b) den; c) der
            """.trimIndent()
        )
        assertEquals(listOf("Ich sitze in ___ Garten."), read.map { it.task })
    }

    @Test
    fun aReplyIsReadWhateverItsNumberingBulletsAndMarkdown() {
        val reply = """
            Here are your exercises:
            ```
            1. short || Write the plural. || der Hund || die Hunde
            2 || short || Write the plural. || die Katze || die Katzen
            - **short** || Write the plural. || [der Apfel] || [die Äpfel]
            [4] Short answer || "Write the plural." || das Kind || die Kinder
            5.
            short || Write the plural. || der Baum || die Bäume
            short || Write the plural. || das Buch
            || die Bücher
            ```
        """.trimIndent()
        assertEquals(
            listOf("der Hund" to "die Hunde", "die Katze" to "die Katzen", "der Apfel" to "die Äpfel", "das Kind" to "die Kinder", "der Baum" to "die Bäume", "das Buch" to "die Bücher"),
            parseCustomSheetResponse(reply).map { it.task to it.answer }
        )
        assertTrue(parseCustomSheetResponse(reply).all { it.form == ExerciseForm.ShortAnswer && it.instruction == "Write the plural." })
    }

    @Test
    fun exercisesThatGiveTheirAnswerAwayOrAreEmptyRepeatedOrTheFormatLineAreDropped() {
        val read = parseCustomSheetResponse(
            """
            short || [instruction in English] || [task in German] || [answer in German] || [the German words of the task]
            short || Write the plural. || der Hund || die Hunde
            short || Write the plural. || Der Hund! || die Hunde
            short || Put it in the dative. || mit (die Kinder) || den Kindern
            short || Write the plural. || das Auto (die Autos) || die Autos
            short || Write the plural. || die Katze ||
            short || Write the plural. ||  || die Katzen
            the plural || der Tisch || die Tische
            """.trimIndent()
        )
        assertEquals(listOf("der Hund", "mit (die Kinder)"), read.map { it.task })
    }

    @Test
    fun wordHelpThatNamesTheAnswerIsDroppedAndAtMostThreeAreKept() {
        val read = parseCustomSheetResponse(
            "short || Write the plural. || der große Hund || die großen Hunde || Hunde = dogs; groß = big; a = 1; b = 2; c = 3"
        ).single()
        assertEquals(listOf(WordHelp("groß", "big"), WordHelp("a", "1"), WordHelp("b", "2")), read.wordHelp)
    }

    private fun gap(task: String, answer: String, instruction: String? = "Put it in the dative.") =
        Exercise(ExerciseForm.GapFill, instruction, task, answer)

    @Test
    fun aGapFillLineIsReadWithItsGapWrittenTheSameWayWhateverTheModelTyped() {
        val read = parseCustomSheetResponse(
            "gap || Put it in the dative. || Ich gebe ___ (der Mann) das Buch. || dem Mann || Buch = book\n" +
                "Gap fill || Put it in the dative. || Wir helfen _______ (die Frau). || der Frau\n" +
                "gap-fill || Put it in the dative. || Sie dankt __ (das Kind). || dem Kind || -"
        )
        assertEquals(
            listOf(
                gap("Ich gebe ___ (der Mann) das Buch.", "dem Mann").copy(wordHelp = listOf(WordHelp("Buch", "book"))),
                gap("Wir helfen ___ (die Frau).", "der Frau"),
                gap("Sie dankt ___ (das Kind).", "dem Kind")
            ),
            read
        )
    }

    @Test
    fun aGapFillWithNoGapOrMoreThanOneIsDropped() {
        val read = parseCustomSheetResponse(
            """
            gap || Put it in the dative. || Ich gebe dem Mann das Buch. || dem Mann
            gap || Put it in the dative. || Ich gebe ___ (der Mann) ___ (das Buch). || dem Mann, das Buch
            gap || Put it in the dative. || Ich helfe ___ (der Mann). || dem Mann
            """.trimIndent()
        )
        assertEquals(listOf("Ich helfe ___ (der Mann)."), read.map { it.task })
    }

    @Test
    fun aGapFillMayShowItsAnswerOnlyInTheBaseFormHint() {
        val read = parseCustomSheetResponse(
            """
            gap || Fill the gap. || Ich sehe ___ (die Katze). || die Katze
            gap || Fill the gap. || Die Katze schläft, ich sehe ___ (die Katze). || die Katze
            gap || Fill the gap. || Ich gebe dem Mann ___ (der Mann). || dem Mann
            short || Write the plural. || das Auto (die Autos) || die Autos
            """.trimIndent()
        )
        // A feminine noun keeps its article in the accusative: the hint gives the answer away, and that is the drill.
        assertEquals(listOf("Ich sehe ___ (die Katze)."), read.map { it.task })
    }

    @Test
    fun aGapFillsWordHelpNeverNamesTheAnswerAndKeepsThreeWordsAtMost() {
        val read = parseCustomSheetResponse(
            "gap || Put it in the dative. || Ich gebe ___ (der alte Mann) heute das rote Buch. || dem alten Mann || " +
                "Mann = man; alten = old; heute = today; rote = red; Buch = book"
        ).single()
        assertEquals(listOf(WordHelp("heute", "today"), WordHelp("rote", "red"), WordHelp("Buch", "book")), read.wordHelp)
    }

    @Test
    fun aGapFillPrintsItsGapWideEnoughToWriteTheAnswerInAndTheKeyFillsItIn() {
        val short = gap("Ich ___ (sein) müde.", "bin")
        val long = gap("Ich gebe ___ (der alte Mann) das Buch.", "dem alten Mann")
        // The gap is a writing line: wide enough for a short word, wider for a longer answer.
        assertEquals("Ich ________ (sein) müde.", printedGapFill(short.task, short.answer))
        assertEquals("Ich gebe ______________________ (der alte Mann) das Buch.", printedGapFill(long.task, long.answer))
        // The answer key shows the whole sentence with the answer in the gap and no hint.
        assertEquals("Ich bin müde.", filledGapFill(short.task, short.answer))
        assertEquals("Ich gebe dem alten Mann das Buch.", filledGapFill(long.task, long.answer))
        assertEquals("Wir helfen der Frau.", filledGapFill("Wir helfen ___ (die Frau).", "der Frau"))
    }

    @Test
    fun aGapFillPromptSaysWhatAGapLooksLike() {
        val prompt = buildCustomSheetPrompt(
            brief = "Nominative → dative", forms = listOf(ExerciseForm.GapFill, ExerciseForm.ShortAnswer), cards = cards,
            targetLanguage = "German", nativeLanguage = "English", count = 4, difficulty = WorksheetDifficulty.Medium
        )
        val rule = prompt.lines().single { it.startsWith("- A \"gap\" exercise") }
        assertTrue("exactly one gap, written as ___" in rule)
        assertTrue("base form" in rule && "round brackets right after the gap" in rule)
        assertTrue("Its answer is exactly what goes in the gap" in rule)
        assertTrue(prompt.endsWith("\n[form: gap, short] || [instruction in English] || [task in German] || [answer in German] || " +
            "[the German words of the task that are not on the flashcards, each with its English meaning, as \"word = meaning; word = meaning\", or - if there are none]"))
        // With nothing ticked, a sheet mixes every form there is.
        assertEquals(listOf(ExerciseForm.GapFill, ExerciseForm.ShortAnswer), customSheetForms(emptySet()).filter { it != ExerciseForm.MultipleChoice })
    }

    @Test
    fun thePickKeepsTheSheetsFormsInTheModelsOrderAndLetsNearRepeatsWait() {
        val candidates = listOf(
            short("Ich sehe den großen Hund im Park.", "a"),
            Exercise(ExerciseForm.GapFill, null, "Ich ___ müde.", "bin"),
            short("Ich sehe den großen Hund im Garten.", "b"),
            short("die Katze", "die Katzen"),
            short("das Kind", "die Kinder")
        )
        // A gap fill on a short-answer sheet is left out; the near-repeat only comes in when the sheet needs it.
        assertEquals(listOf(candidates[0], candidates[3], candidates[4]), pickExercises(candidates, listOf(ExerciseForm.ShortAnswer), count = 3))
        assertEquals(listOf(candidates[0], candidates[2], candidates[3], candidates[4]), pickExercises(candidates, listOf(ExerciseForm.ShortAnswer), count = 10))
    }

    @Test
    fun thePickSharesASheetFairlyBetweenItsForms() {
        val gaps = (1..4).map { Exercise(ExerciseForm.GapFill, null, "Satz $it ___.", "x$it") }
        val shorts = (1..4).map { short("Wort $it", "y$it") }
        val picked = pickExercises(gaps + shorts, listOf(ExerciseForm.GapFill, ExerciseForm.ShortAnswer), count = 4)
        assertEquals(listOf(gaps[0], gaps[1], shorts[0], shorts[1]), picked)
    }

    @Test
    fun formTicksStartMixedNarrowToWhatIsTickedAndNeverLeaveNone() {
        val gap = ExerciseForm.GapFill
        val choice = ExerciseForm.MultipleChoice
        val short = ExerciseForm.ShortAnswer
        // Nothing stored is "Mixed: let the AI choose", with every form allowed.
        assertTrue(isMixed(emptySet()))
        assertEquals(setOf(gap, choice, short), tickedForms(emptySet()))
        // Unticking a form of a mixed sheet keeps the other two.
        assertEquals(setOf(gap, short), tickForm(emptySet(), choice))
        assertFalse(isMixed(setOf(gap, short)))
        // The last ticked form can't be unticked: a sheet needs a form.
        assertEquals(setOf(short), tickForm(setOf(short), short))
        // Ticking the third form again is mixed, stored as nothing, the default.
        assertEquals(emptySet(), tickForm(setOf(gap, short), choice))
        assertTrue(isMixed(setOf(gap, choice, short)))
    }

    @Test
    fun theSheetPrintsExercisesGroupedByFormNumberedThroughWithAnInstructionWhereItChanges() {
        val plural1 = short("der Hund", "die Hunde")
        val dative = short("mit (der Mann)", "mit dem Mann", instruction = "Put it in the dative.")
        val plural2 = short("die Katze", "die Katzen")
        val gap = Exercise(ExerciseForm.GapFill, "Fill the gap.", "Ich ___ müde. (sein)", "bin")
        val rows = customSheetLayout(listOf(plural1, plural2, dative, gap, short("das Kind", "die Kinder", instruction = "Put it in the dative.")))
        assertEquals(listOf(1, 2, 3, 4, 5), rows.map { it.number })
        assertEquals(listOf(gap, plural1, plural2, dative), rows.take(4).map { it.exercise })
        assertEquals(listOf("Fill the gap.", "Write the plural.", null, "Put it in the dative.", null), rows.map { it.instruction })
    }

    @Test
    fun multipleChoiceOptionsAreLetteredOnOneLineAndTheKeyGivesTheLetterAndTheWord() {
        val exercise = choice("Ich fahre mit ___ Zug.", "dem", "den", "dem", "der")
        assertEquals("a) den     b) dem     c) der", choiceLine(exercise.options))
        assertEquals("b) dem", keyAnswer(exercise))
        // Every other form's key is its answer as it is.
        assertEquals("die Hunde", keyAnswer(short("der Hund", "die Hunde")))
    }

    @Test
    fun anAnswerBoxIsAsTallAsTheKeyAnswerIsLong() {
        assertEquals(1, answerBoxLines("die Hunde"))
        assertEquals(1, answerBoxLines("a".repeat(25)))
        assertEquals(2, answerBoxLines("Ich habe gestern den Hund gesehen."))
        // A conjugation table on lines of its own gets a line for each.
        assertEquals(3, answerBoxLines("ich bin\ndu bist\ner ist"))
        assertEquals(ANSWER_BOX_MAX_LINES, answerBoxLines("lang ".repeat(200)))
    }
}
