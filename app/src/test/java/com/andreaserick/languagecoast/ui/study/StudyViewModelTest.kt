package com.andreaserick.languagecoast.ui.study

import androidx.lifecycle.SavedStateHandle
import com.andreaserick.languagecoast.data.AlternativeVerdict
import com.andreaserick.languagecoast.data.CardEdit
import com.andreaserick.languagecoast.data.CardVerdict
import com.andreaserick.languagecoast.data.DeletedContent
import com.andreaserick.languagecoast.data.TranslationEngine
import com.andreaserick.languagecoast.data.TranslationProvider
import com.andreaserick.languagecoast.data.TranslationRequest
import com.andreaserick.languagecoast.data.TranslationResult
import com.andreaserick.languagecoast.data.Translator
import com.andreaserick.languagecoast.data.Completion
import com.andreaserick.languagecoast.data.DAILY_REVIEW_CAP
import com.andreaserick.languagecoast.data.Grade
import com.andreaserick.languagecoast.data.Pace
import com.andreaserick.languagecoast.data.SuggestedSentence
import com.andreaserick.languagecoast.data.WELCOME_BACK_NEW_SENTENCES
import com.andreaserick.languagecoast.data.WELCOME_BACK_REVIEW_CAP
import com.andreaserick.languagecoast.testing.FakeFlashcardRepository
import com.andreaserick.languagecoast.testing.FakeSettingsRepository
import com.andreaserick.languagecoast.testing.FakeSpeaker
import com.andreaserick.languagecoast.testing.FakeTranslator
import com.andreaserick.languagecoast.testing.MainDispatcherRule
import com.andreaserick.languagecoast.testing.TEST_CLOCK
import com.andreaserick.languagecoast.testing.TEST_TIME_ZONE
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StudyViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val flashcards = FakeFlashcardRepository()
    private val settings = FakeSettingsRepository()
    private val speaker = FakeSpeaker()

    private val translator = FakeTranslator()

    private fun TestScope.createViewModel(islandId: Int = 1, scope: String = "island", coastId: Int = 0): StudyViewModel {
        val viewModel = StudyViewModel(
            SavedStateHandle(mapOf("islandId" to islandId, "islandName" to "Island $islandId", "scope" to scope, "coastId" to coastId)),
            flashcards,
            settings,
            TEST_CLOCK,
            TEST_TIME_ZONE,
            speaker,
            translator
        )
        // uiState is only computed while collected (WhileSubscribed).
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private val StudyViewModel.state get() = uiState.value
    private val StudyViewModel.currentText get() = state.currentCard?.nativeText

    @Test
    fun sessionStartsWithAllCardsOfTheIsland() = runTest {
        flashcards.seed(islandId = 1, cardCount = 3)
        flashcards.seed(islandId = 2, cardCount = 5)

        val viewModel = createViewModel(islandId = 1)

        assertEquals("Island 1", viewModel.state.islandName)
        assertEquals(3, viewModel.state.sessionCards.size)
        assertEquals("n1", viewModel.currentText)
        assertFalse(viewModel.state.isLoading)
    }

    @Test
    fun emptyIslandShowsNoCards() = runTest {
        val viewModel = createViewModel()

        assertFalse(viewModel.state.hasCards)
        assertFalse(viewModel.state.isSessionComplete)
    }

    @Test
    fun againMovesCurrentCardToTheEnd() = runTest {
        flashcards.seed(islandId = 1, cardCount = 3)
        val viewModel = createViewModel()

        viewModel.onGrade(Grade.Again)

        assertEquals(listOf("n2", "n3", "n1"), viewModel.state.sessionCards.map { it.card.nativeText })
        assertEquals("n2", viewModel.currentText)
    }

    @Test
    fun passingEveryCardCompletesSessionAndRecordsStreakOnce() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        val viewModel = createViewModel()

        viewModel.onGrade(Grade.Good)
        assertFalse(viewModel.state.isSessionComplete)
        viewModel.onGrade(Grade.Good)

        assertTrue(viewModel.state.isSessionComplete)
        assertEquals(1, settings.studySessionsRecorded)
    }

    @Test
    fun completingSessionMarksIslandStudiedAndReportsStats() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        val viewModel = createViewModel()

        viewModel.onGrade(Grade.Again)
        assertEquals(0f, viewModel.state.progress)
        viewModel.onGrade(Grade.Good)
        assertEquals(0.5f, viewModel.state.progress)
        viewModel.onGrade(Grade.Good)

        assertTrue(viewModel.state.isSessionComplete)
        assertEquals(2, viewModel.state.totalCards)
        assertEquals(1, viewModel.state.againCount)
        assertEquals(TEST_CLOCK.now().toEpochMilliseconds(), flashcards.islands.value.single().lastStudied)
    }

    @Test
    fun deletedCardCanBeRestored() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        val viewModel = createViewModel()
        var deleted: DeletedContent? = null

        viewModel.deleteCurrentCard { deleted = it }
        assertEquals(listOf("n2"), viewModel.state.sessionCards.map { it.card.nativeText })

        viewModel.restore(deleted!!)
        assertEquals(listOf("n1", "n2"), flashcards.cards.value.map { it.nativeText })
    }

    @Test
    fun practiceAllBringsBackAllCards() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        val viewModel = createViewModel()
        viewModel.onGrade(Grade.Good)
        viewModel.onGrade(Grade.Good)

        viewModel.practiceAll()

        assertFalse(viewModel.state.isSessionComplete)
        assertEquals(2, viewModel.state.sessionCards.size)
        assertEquals(0, viewModel.state.currentIndex)
    }

    @Test
    fun nextAndPreviousStayWithinBounds() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        val viewModel = createViewModel()

        viewModel.previous()
        assertEquals(0, viewModel.state.currentIndex)
        assertFalse(viewModel.state.canGoBack)

        viewModel.next()
        viewModel.next()
        assertEquals(1, viewModel.state.currentIndex)
        assertFalse(viewModel.state.canGoForward)
    }

    @Test
    fun passingLastCardKeepsIndexInBounds() = runTest {
        flashcards.seed(islandId = 1, cardCount = 3)
        val viewModel = createViewModel()
        viewModel.next()
        viewModel.next()

        viewModel.onGrade(Grade.Good)

        assertEquals(1, viewModel.state.currentIndex)
        assertEquals("n2", viewModel.currentText)
    }

    @Test
    fun deletingCurrentCardRemovesItFromDatabaseAndSession() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        val viewModel = createViewModel()

        viewModel.deleteCurrentCard {}

        assertEquals(listOf("n2"), flashcards.cards.value.map { it.nativeText })
        assertEquals(listOf("n2"), viewModel.state.sessionCards.map { it.card.nativeText })
    }

    @Test
    fun deletingLastCardOfIslandShowsEmptyStateWithoutRecordingStreak() = runTest {
        flashcards.seed(islandId = 1, cardCount = 1)
        val viewModel = createViewModel()

        viewModel.deleteCurrentCard {}

        assertFalse(viewModel.state.hasCards)
        assertFalse(viewModel.state.isSessionComplete)
        assertEquals(0, settings.studySessionsRecorded)
    }

    @Test
    fun targetLanguageComesFromTheIslandsCoast() = runTest {
        settings.nativeLanguage.value = "German"
        flashcards.seedCoast(coastId = 1, language = "Spanish")
        flashcards.seed(islandId = 1, cardCount = 1, coastId = 1)
        flashcards.seedCoast(coastId = 2, language = "Icelandic")
        flashcards.seed(islandId = 2, cardCount = 1, coastId = 2)

        val viewModel = createViewModel(islandId = 2)

        assertEquals("German", viewModel.state.nativeLanguage)
        assertEquals("Icelandic", viewModel.state.targetLanguage)
    }

    @Test
    fun gradingSavesTheCardsNextReview() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        val viewModel = createViewModel()

        viewModel.onGrade(Grade.Good)

        val card = flashcards.cards.value.first()
        assertEquals(TEST_CLOCK.now().toEpochMilliseconds(), card.lastReviewed)
        // A new card graded "Good" comes back in 3 days, at the start of that day.
        assertEquals(startOfDay("2026-10-12"), card.due)
        assertEquals(null, flashcards.cards.value.last().due)
    }

    @Test
    fun againReschedulesTheCardAndKeepsItInTheSession() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        val viewModel = createViewModel()

        viewModel.onGrade(Grade.Again)

        assertEquals(startOfDay("2026-10-10"), flashcards.cards.value.first().due)
        assertEquals(listOf("n2", "n1"), viewModel.state.sessionCards.map { it.card.nativeText })
    }

    @Test
    fun gradeIntervalsArePreviewedForTheCurrentCard() = runTest {
        flashcards.seed(islandId = 1, cardCount = 1)
        val viewModel = createViewModel()

        assertEquals(
            mapOf(Grade.Again to 1, Grade.Hard to 1, Grade.Good to 3, Grade.Easy to 16),
            viewModel.state.gradeIntervals
        )
    }

    @Test
    fun sessionHasOnlyDueCardsWithOverdueOnesBeforeNewOnes() = runTest {
        flashcards.seed(islandId = 1, cardCount = 4)
        setDue("n2", startOfDay("2026-10-11")) // Not due yet.
        setDue("n3", startOfDay("2026-10-09")) // Due today.
        setDue("n4", startOfDay("2026-10-01")) // Overdue.

        val viewModel = createViewModel()

        assertEquals(listOf("n4", "n3", "n1"), viewModel.state.sessionCards.map { it.card.nativeText })
    }

    @Test
    fun islandWithNothingDueIsCaughtUpUntilPracticingAll() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        setDue("n1", startOfDay("2026-10-11"))
        setDue("n2", startOfDay("2026-10-14"))

        val viewModel = createViewModel()

        assertTrue(viewModel.state.isCaughtUp)
        assertFalse(viewModel.state.isSessionComplete)
        assertEquals(2, viewModel.state.nextDueInDays)

        viewModel.practiceAll()

        assertFalse(viewModel.state.isCaughtUp)
        assertEquals(2, viewModel.state.sessionCards.size)
    }

    @Test
    fun speakingReadsTheTranslationInTheCoastLanguage() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        val viewModel = createViewModel()

        assertTrue(viewModel.state.canSpeak)
        viewModel.speakAnswer()

        assertEquals(listOf("t1" to "Spanish"), speaker.spoken)
    }

    @Test
    fun noVoiceForTheLanguageMeansNothingIsRead() = runTest {
        speaker.available = emptySet()
        flashcards.seed(islandId = 1, cardCount = 1)
        val viewModel = createViewModel()

        assertFalse(viewModel.state.canSpeak)
        viewModel.speakAnswer()

        assertTrue(speaker.spoken.isEmpty())
    }

    @Test
    fun gradingStopsReadingTheOldAnswer() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        val viewModel = createViewModel()

        viewModel.onGrade(Grade.Good)

        assertEquals(1, speaker.stops)
    }

    @Test
    fun makingAnAlternativeMainSwapsItWithTheTranslation() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        flashcards.cards.update { all -> all.map { if (it.nativeText == "n1") it.copy(alternatives = listOf("a1", "b1")) else it } }
        val viewModel = createViewModel()

        viewModel.makeMainTranslation("b1")

        val card = viewModel.state.currentCard!!
        assertEquals("b1", card.targetText)
        assertEquals(listOf("a1", "t1"), card.alternatives)
        assertEquals("b1", flashcards.cards.value.first().targetText)
        // Still the same card in the same place in the session.
        assertEquals(listOf("n1", "n2"), viewModel.state.sessionCards.map { it.card.nativeText })
    }

    @Test
    fun alternativesCanBeReadAloud() = runTest {
        flashcards.seed(islandId = 1, cardCount = 1)
        val viewModel = createViewModel()

        viewModel.speak("a1")

        assertEquals(listOf("a1" to "Spanish"), speaker.spoken)
    }

    @Test
    fun editingTheCurrentCardUpdatesItInPlace() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        val viewModel = createViewModel()

        viewModel.editCard(viewModel.state.currentCard!!.cardId, CardEdit("hello", "hola", emptyList(), null, islandId = 1))

        assertEquals("hola", viewModel.state.currentCard!!.targetText)
        assertEquals(listOf("hello", "n2"), viewModel.state.sessionCards.map { it.card.nativeText })
    }

    @Test
    fun movingTheCurrentCardToAnotherIslandTakesItOutOfTheSession() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        flashcards.seed(islandId = 2, cardCount = 1)
        val viewModel = createViewModel()
        assertEquals(listOf(1, 2), viewModel.state.coastIslands.map { it.islandId })

        val card = viewModel.state.currentCard!!
        viewModel.editCard(card.cardId, CardEdit(card.nativeText, card.targetText, emptyList(), null, islandId = 2))

        assertEquals(listOf("n2"), viewModel.state.sessionCards.map { it.card.nativeText })
        assertEquals(1, viewModel.state.totalCards)
    }

    @Test
    fun studyDirectionIsRememberedAndKeepsTheSession() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        val viewModel = createViewModel()
        assertFalse(viewModel.state.isReversed)
        viewModel.next()

        viewModel.setReversed(true)

        assertTrue(viewModel.state.isReversed)
        assertTrue(settings.studyReversed.value)
        assertEquals("n2", viewModel.currentText)
    }

    @Test
    fun todaySessionTakesDueCardsFromEveryCoastInTurns() = runTest {
        flashcards.seed(islandId = 1, cardCount = 3, coastId = 1, studied = true)
        flashcards.seedCoast(coastId = 2, language = "Icelandic")
        flashcards.seed(islandId = 2, cardCount = 2, coastId = 2, studied = true)
        setDue("n3", startOfDay("2026-10-11")) // Not due yet.

        val viewModel = createViewModel(scope = "today")

        assertEquals(listOf("Spanish", "Icelandic", "Spanish", "Icelandic"), viewModel.state.sessionCards.map { it.language })
        assertEquals("Spanish", viewModel.state.targetLanguage)
        viewModel.next()
        assertEquals("Icelandic", viewModel.state.targetLanguage)
    }

    @Test
    fun completingTodaySessionMarksEveryIslandStudied() = runTest {
        flashcards.seed(islandId = 1, cardCount = 1, coastId = 1, studied = true)
        flashcards.seed(islandId = 2, cardCount = 1, coastId = 1, studied = true)
        val viewModel = createViewModel(scope = "today")

        viewModel.onGrade(Grade.Good)
        viewModel.onGrade(Grade.Good)

        assertTrue(viewModel.state.isSessionComplete)
        assertTrue(flashcards.islands.value.all { it.lastStudied == TEST_CLOCK.now().toEpochMilliseconds() })
        assertEquals(1, settings.studySessionsRecorded)
    }

    private fun suggest(vararg sentences: String) {
        flashcards.suggestions.value = sentences.mapIndexed { i, text ->
            SuggestedSentence(suggestionId = i + 1, coastId = 1, nativeText = text, targetText = "$text!", category = "Travel", emoji = "✈️")
        }
    }

    @Test
    fun dailySessionReviewsACappedNumberThenOffersNewSentences() = runTest {
        flashcards.seed(islandId = 1, cardCount = DAILY_REVIEW_CAP + 5, studied = true)
        suggest("s1", "s2", "s3", "s4")
        val viewModel = createViewModel(scope = "daily", coastId = 1)

        assertEquals(DAILY_REVIEW_CAP, viewModel.state.sessionCards.size)
        assertEquals(3, viewModel.state.learnTotal)
        assertEquals(null, viewModel.state.learning)
        assertEquals("Spanish", viewModel.state.targetLanguage)
        assertFalse(viewModel.state.isWelcomeBack)

        repeat(DAILY_REVIEW_CAP) { viewModel.onGrade(Grade.Good) }

        assertEquals("s1", viewModel.state.learning?.nativeText)
        assertEquals(3, viewModel.state.learnLeft)
        assertFalse(viewModel.state.isSessionComplete)
    }

    @Test
    fun keptSentencesBecomeCardsAndArePracticedAtTheEnd() = runTest {
        flashcards.seed(islandId = 1, cardCount = 1, studied = true)
        suggest("s1", "s2", "s3")
        val viewModel = createViewModel(scope = "daily", coastId = 1)
        viewModel.onGrade(Grade.Good)

        viewModel.keepSuggestion()
        viewModel.skipSuggestion()
        assertEquals("s3", viewModel.state.learning?.nativeText)
        viewModel.keepSuggestion()

        // The two kept sentences are now cards on the coast's Travel island, up for a first practice.
        assertEquals(null, viewModel.state.learning)
        assertEquals(listOf("s1", "s3"), viewModel.state.sessionCards.map { it.card.nativeText })
        assertEquals("Travel", viewModel.state.currentPlaced?.islandName)
        assertEquals(listOf("s1", "s3"), flashcards.cards.value.drop(1).map { it.nativeText })
        assertEquals(listOf(2), flashcards.suggestions.value.map { it.suggestionId })
        assertTrue(flashcards.suggestions.value.single().skipped)
        assertFalse(viewModel.state.isSessionComplete)
        assertEquals(0, settings.studySessionsRecorded)

        viewModel.onGrade(Grade.Good)
        viewModel.onGrade(Grade.Good)

        assertTrue(viewModel.state.isSessionComplete)
        assertEquals(2, viewModel.state.keptCount)
        assertEquals(3, viewModel.state.totalCards)
        assertEquals(1, settings.studySessionsRecorded)
        assertTrue(flashcards.islands.value.all { it.lastStudied == TEST_CLOCK.now().toEpochMilliseconds() })
    }

    @Test
    fun dailySessionOnAnEmptyCoastStartsWithTheNewSentences() = runTest {
        flashcards.seedCoast(coastId = 1, language = "Spanish")
        suggest("s1", "s2")
        val viewModel = createViewModel(scope = "daily", coastId = 1)

        assertTrue(viewModel.state.hasCards)
        assertFalse(viewModel.state.isCaughtUp)
        assertEquals("s1", viewModel.state.learning?.nativeText)
        assertEquals("Spanish", viewModel.state.targetLanguage)

        viewModel.skipSuggestion()
        viewModel.skipSuggestion()

        assertTrue(viewModel.state.isSessionComplete)
        assertEquals(0, viewModel.state.keptCount)
    }

    @Test
    fun dailySessionAfterDaysAwayIsSmall() = runTest {
        flashcards.seed(islandId = 1, cardCount = 10, studied = true)
        suggest("s1", "s2", "s3")
        settings.lastStudyDate.value = LocalDate(2026, 10, 1)
        val viewModel = createViewModel(scope = "daily", coastId = 1)

        assertTrue(viewModel.state.isWelcomeBack)
        assertEquals(WELCOME_BACK_REVIEW_CAP, viewModel.state.sessionCards.size)
        assertEquals(WELCOME_BACK_NEW_SENTENCES, viewModel.state.learnTotal)
    }

    @Test
    fun dailySessionWithNothingDueAndNothingReadyIsCaughtUp() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        setDue("n1", startOfDay("2026-10-12"))
        setDue("n2", startOfDay("2026-10-12"))
        val viewModel = createViewModel(scope = "daily", coastId = 1)

        assertTrue(viewModel.state.isCaughtUp)
        assertFalse(viewModel.state.isSessionComplete)
    }

    @Test
    fun coastSessionCoversOnlyThatCoast() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2, coastId = 1, studied = true)
        flashcards.seedCoast(coastId = 2, language = "Icelandic")
        flashcards.seed(islandId = 2, cardCount = 2, coastId = 2, studied = true)

        val viewModel = createViewModel(scope = "coast", coastId = 2)

        assertEquals(listOf("n1", "n2"), viewModel.state.sessionCards.map { it.card.nativeText })
        assertTrue(viewModel.state.sessionCards.all { it.coastId == 2 })
    }

    @Test
    fun aCoastSessionReviewsOnlyAndLeavesNewCardsToTheDailySession() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2, studied = true)
        flashcards.seed(islandId = 2, cardCount = 30) // A whole island added at once: new cards, not a pile of due ones.

        val coast = createViewModel(scope = "coast", coastId = 1)
        assertEquals(2, coast.state.sessionCards.size)
        assertTrue(coast.state.sessionCards.all { it.card.islandId == 1 })

        // Opening the island itself is how to learn it in one go.
        assertEquals(30, createViewModel(islandId = 2).state.sessionCards.size)
    }

    @Test
    fun theDailySessionIntroducesNewCardsAtThePaceAndKnownOnesAreWavedThrough() = runTest {
        flashcards.seed(islandId = 1, cardCount = 1, studied = true)
        flashcards.seed(islandId = 2, cardCount = 30)
        suggest("s1", "s2")
        val viewModel = createViewModel(scope = "daily", coastId = 1)

        // One review, then three of the thirty new cards; the suggested sentences wait, the coast's own cards come first.
        assertEquals(1, viewModel.state.sessionCards.size)
        assertEquals(3, viewModel.state.introTotal)
        assertEquals(0, viewModel.state.learnTotal)
        assertEquals(null, viewModel.state.introducing)
        viewModel.onGrade(Grade.Good)

        val first = viewModel.state.introducing!!
        assertEquals("n1", first.card.nativeText)
        assertEquals(2, first.card.islandId)
        viewModel.learnIntroduced()
        val known = viewModel.state.introducing!!.card
        viewModel.knowIntroduced()
        viewModel.learnIntroduced()

        // The known card is scheduled days ahead without being practiced; the two taken on are practiced now.
        val saved = flashcards.cards.value.first { it.cardId == known.cardId }
        assertTrue(saved.due!! > TEST_CLOCK.now().toEpochMilliseconds())
        assertEquals(null, viewModel.state.introducing)
        assertEquals(listOf("n1", "n3"), viewModel.state.sessionCards.map { it.card.nativeText })
        assertEquals(2, viewModel.state.learnedCount)
        assertFalse(viewModel.state.isSessionComplete)

        viewModel.onGrade(Grade.Good)
        viewModel.onGrade(Grade.Good)

        assertTrue(viewModel.state.isSessionComplete)
        assertEquals(1, settings.studySessionsRecorded)
        assertEquals(27, flashcards.cards.value.count { it.due == null })
    }

    @Test
    fun aFasterPaceIntroducesMoreNewCardsADay() = runTest {
        flashcards.seed(islandId = 1, cardCount = 30)
        settings.pace.value = Pace.Sprint

        val viewModel = createViewModel(scope = "daily", coastId = 1)

        assertEquals(20, viewModel.state.introTotal)
        assertEquals("n1", viewModel.state.introducing?.card?.nativeText)
    }

    @Test
    fun leakySessionHasTheForgottenCardsMostForgottenFirst() = runTest {
        flashcards.seed(islandId = 1, cardCount = 3)
        flashcards.cards.update { all -> all.map { if (it.nativeText == "n1") it.copy(lapses = 2, due = Long.MAX_VALUE) else if (it.nativeText == "n3") it.copy(lapses = 4) else it } }

        val viewModel = createViewModel(scope = "leaky")

        assertEquals(listOf("n3", "n1"), viewModel.state.sessionCards.map { it.card.nativeText })
        assertFalse(viewModel.state.isCaughtUp)
    }

    @Test
    fun nothingLeakyIsReportedAsCaughtUp() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)

        val viewModel = createViewModel(scope = "leaky")

        assertTrue(viewModel.state.isCaughtUp)
    }

    @Test
    fun explainingAWordAsksTheProviderAndShowsItsAnswer() = runTest {
        flashcards.seed(islandId = 1, cardCount = 1)
        settings.apiKey.value = "key"
        translator.completion = Completion("It means hello.")
        val viewModel = createViewModel()

        viewModel.explainWord("t1")

        val explanation = viewModel.state.explanation!!
        assertEquals("t1", explanation.word)
        assertEquals("It means hello.", explanation.text)
        assertTrue(translator.prompts.single().contains("\"t1\""))

        viewModel.dismissExplanation()
        assertEquals(null, viewModel.state.explanation)
    }

    @Test
    fun checkingACardSuggestsWhatPeopleSayAndKeepsTheOldTranslationAsAnAlternative() = runTest {
        flashcards.seed(islandId = 1, cardCount = 1)
        flashcards.coasts.update { all -> all.map { it.copy(region = "Mexico") } }
        settings.apiKey.value = "key"
        translator.completionFor = { prompt ->
            if (prompt.startsWith("Spanish text: t1")) Completion("How are you?")
            else Completion("VERDICT: CHANGE\nBETTER: ¿Qué onda?\nWHY: In Mexico this is the casual greeting.\nCURRENT: OK")
        }
        val viewModel = createViewModel()

        viewModel.checkCard()

        val check = viewModel.state.cardCheck!!
        assertEquals(CardVerdict.Better("¿Qué onda?", "In Mexico this is the casual greeting.", currentIsWrong = false), check.verdict)
        assertEquals("How are you?", check.readsAs)
        // The card is read back first, without what it should mean; the verdict then has that reading in view.
        val (readingPrompt, checkPrompt) = translator.prompts
        assertFalse("n1" in readingPrompt)
        assertTrue("native speaker of Spanish from Mexico" in checkPrompt)
        assertTrue("the Spanish says: How are you?" in checkPrompt)
        assertEquals("t1", viewModel.state.currentCard?.targetText)

        viewModel.applyCardCheck()

        val card = viewModel.state.currentCard!!
        assertEquals("¿Qué onda?", card.targetText)
        assertEquals(listOf("t1"), card.alternatives)
        assertEquals("In Mexico this is the casual greeting.", card.note)
        assertEquals(null, viewModel.state.cardCheck)
    }

    @Test
    fun aTranslationTheCheckCallsWrongIsReplacedNotKept() = runTest {
        flashcards.seed(islandId = 1, cardCount = 1)
        flashcards.cards.update { all -> all.map { it.copy(alternatives = listOf("a1"), note = "a1 is informal.") } }
        settings.apiKey.value = "key"
        translator.completion = Completion("BETTER: t1 bien\nWHY: The verb was missing.\nCURRENT: WRONG")
        val viewModel = createViewModel()

        viewModel.checkCard()
        viewModel.applyCardCheck()

        val card = viewModel.state.currentCard!!
        assertEquals("t1 bien", card.targetText)
        assertEquals(listOf("a1"), card.alternatives)
        assertEquals("The verb was missing. a1 is informal.", card.note)
    }

    @Test
    fun aCardTheCheckFindsFineIsLeftAlone() = runTest {
        flashcards.seed(islandId = 1, cardCount = 1)
        settings.apiKey.value = "key"
        translator.completion = Completion("FINE")
        val viewModel = createViewModel()

        viewModel.checkCard()
        assertEquals(CardVerdict.Fine, viewModel.state.cardCheck?.verdict)

        viewModel.applyCardCheck()
        assertEquals("t1", viewModel.state.currentCard?.targetText)

        viewModel.dismissCardCheck()
        assertEquals(null, viewModel.state.cardCheck)
    }

    @Test
    fun aCheckThatCannotBeAnsweredSaysWhy() = runTest {
        flashcards.seed(islandId = 1, cardCount = 1)
        val viewModel = createViewModel()

        // No API key yet.
        viewModel.checkCard()
        assertTrue(viewModel.state.cardCheck?.error.orEmpty().contains("API key"))

        settings.apiKey.value = "key"
        translator.completion = Completion("Let me think about this card.")
        viewModel.checkCard()
        assertTrue(viewModel.state.cardCheck?.error.orEmpty().contains("couldn't be read"))
        assertEquals(null, viewModel.state.cardCheck?.verdict)
    }

    /** Answers the card check with FINE and a question about another translation with [alternativeReply]. */
    private fun answerAlternativeWith(alternativeReply: String) {
        settings.apiKey.value = "key"
        translator.completionFor = { prompt -> Completion(if ("correct as well" in prompt) alternativeReply else "VERDICT: FINE") }
    }

    @Test
    fun anotherTranslationTheProviderFindsCorrectCanBeSavedAsAnAlternative() = runTest {
        flashcards.seed(islandId = 1, cardCount = 1)
        flashcards.coasts.update { all -> all.map { it.copy(region = "Mexico") } }
        flashcards.cards.update { all -> all.map { it.copy(alternatives = listOf("a1"), note = "a1 is informal.") } }
        answerAlternativeWith("VERDICT: YES\nWHY: \"mine\" is what you say to friends.\nFIXED: NONE")
        val viewModel = createViewModel()
        viewModel.checkCard()
        assertTrue(viewModel.state.cardCheck!!.canAskAlternative)
        assertEquals("Spanish", viewModel.state.cardCheck?.targetLanguage)

        viewModel.askAlternative("  mine  ")

        val asked = viewModel.state.cardCheck?.alternative!!
        assertEquals("mine", asked.text)
        assertEquals(AlternativeVerdict.Correct("\"mine\" is what you say to friends."), asked.verdict)
        assertEquals("mine", asked.savable)
        val prompt = translator.prompts.last()
        assertTrue("native speaker of Spanish from Mexico" in prompt)
        assertTrue("English: n1" in prompt && "Spanish: t1" in prompt && "correct as well: mine" in prompt)
        // Asking changes nothing on the card.
        assertEquals(listOf("a1"), viewModel.state.currentCard?.alternatives)

        viewModel.saveAlternative()
        viewModel.saveAlternative() // A second tap adds nothing.

        val card = viewModel.state.currentCard!!
        assertEquals("t1", card.targetText)
        assertEquals(listOf("a1", "mine"), card.alternatives)
        assertEquals("a1 is informal. \"mine\" is what you say to friends.", card.note)
        assertEquals("mine", viewModel.state.cardCheck?.alternative?.saved)
        // The check itself is still what it was.
        assertEquals(CardVerdict.Fine, viewModel.state.cardCheck?.verdict)
    }

    @Test
    fun aRamblingRemarkOnAnAlternativeIsNotKeptAsTheCardsNote() = runTest {
        flashcards.seed(islandId = 1, cardCount = 1)
        answerAlternativeWith("VERDICT: YES\nWHY: " + "The learner's sentence is correct and matches the intended meaning. ".repeat(4))
        val viewModel = createViewModel()
        viewModel.checkCard()

        viewModel.askAlternative("mine")
        viewModel.saveAlternative()

        val card = viewModel.state.currentCard!!
        assertEquals(listOf("mine"), card.alternatives)
        assertEquals(null, card.note)
    }

    @Test
    fun anotherTranslationThatIsWrongCanOnlyBeSavedInItsCorrectedForm() = runTest {
        flashcards.seed(islandId = 1, cardCount = 1)
        answerAlternativeWith("VERDICT: NO\nWHY: The verb is missing.\nFIXED: mine bien")
        val viewModel = createViewModel()
        viewModel.checkCard()

        viewModel.askAlternative("mine")

        val asked = viewModel.state.cardCheck?.alternative!!
        assertEquals(AlternativeVerdict.Incorrect("The verb is missing.", "mine bien"), asked.verdict)
        assertEquals("mine bien", asked.savable)

        viewModel.saveAlternative()

        val card = viewModel.state.currentCard!!
        assertEquals(listOf("mine bien"), card.alternatives)
        // What was wrong with the user's sentence says nothing about the alternative, so it isn't the card's note.
        assertEquals(null, card.note)

        // Without a correction there is nothing to save.
        answerAlternativeWith("VERDICT: NO\nWHY: It means something else.\nFIXED: NONE")
        viewModel.askAlternative("other")
        assertEquals(null, viewModel.state.cardCheck?.alternative?.savable)
        viewModel.saveAlternative()
        assertEquals(listOf("mine bien"), viewModel.state.currentCard?.alternatives)
    }

    @Test
    fun aTranslationTheCardAlreadyHasIsAnsweredWithoutAsking() = runTest {
        flashcards.seed(islandId = 1, cardCount = 1)
        flashcards.cards.update { all -> all.map { it.copy(alternatives = listOf("Otra forma")) } }
        answerAlternativeWith("VERDICT: YES")
        val viewModel = createViewModel()
        viewModel.checkCard()
        val requests = translator.prompts.size

        viewModel.askAlternative("T1!")
        assertEquals(AlternativeVerdict.OnTheCard, viewModel.state.cardCheck?.alternative?.verdict)
        viewModel.askAlternative("otra forma")
        assertEquals(AlternativeVerdict.OnTheCard, viewModel.state.cardCheck?.alternative?.verdict)
        viewModel.askAlternative("   ")

        assertEquals(requests, translator.prompts.size)
        assertEquals(null, viewModel.state.cardCheck?.alternative?.savable)
    }

    @Test
    fun aQuestionAboutAnotherTranslationThatCannotBeAnsweredSaysWhy() = runTest {
        flashcards.seed(islandId = 1, cardCount = 1)
        val viewModel = createViewModel()

        // Without a provider that can answer, there is nobody to ask.
        viewModel.checkCard()
        assertFalse(viewModel.state.cardCheck!!.canAskAlternative)
        viewModel.askAlternative("mine")
        assertEquals(null, viewModel.state.cardCheck?.alternative)

        answerAlternativeWith("Let me think about it.")
        viewModel.checkCard()
        viewModel.askAlternative("mine")
        assertTrue(viewModel.state.cardCheck?.alternative?.error.orEmpty().contains("couldn't be read"))

        translator.completionFor = { Completion.failure("No internet connection.") }
        viewModel.askAlternative("mine")
        assertEquals("No internet connection.", viewModel.state.cardCheck?.alternative?.error)

        // Checking again starts over.
        viewModel.checkCard()
        assertEquals(null, viewModel.state.cardCheck?.alternative)
    }

    @Test
    fun fillTheGapBlanksTheLongestWordOfTheTranslation() = runTest {
        flashcards.seed(islandId = 1, cardCount = 1)
        flashcards.cards.update { all -> all.map { it.copy(targetText = "Wo ist der Strand?") } }
        val viewModel = createViewModel()

        viewModel.setMode(StudyMode.Cloze)

        assertEquals("Strand", viewModel.state.cloze?.word)
        assertEquals("Wo ist der ______?", viewModel.state.cloze?.display)
    }

    private fun setDue(nativeText: String, due: Long) {
        flashcards.cards.update { all -> all.map { if (it.nativeText == nativeText) it.copy(due = due) else it } }
    }

    private fun startOfDay(date: String): Long =
        LocalDate.parse(date).atStartOfDayIn(TEST_TIME_ZONE).toEpochMilliseconds()

    @Test
    fun aQuestionAboutTheCardIsAnsweredAndAFollowUpKnowsWhatWasSaid() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        settings.apiKey.value = "key"
        translator.completion = Completion("**t1** is a greeting.")
        val viewModel = createViewModel()

        viewModel.openCardQuestions()
        val open = viewModel.state.cardQuestions!!
        assertEquals("n1" to "t1", open.nativeText to open.targetText)
        assertEquals(null, open.unavailable)
        assertTrue(open.asked.isEmpty())

        viewModel.askAboutCard(" When would I say this? ")
        assertEquals(listOf(AskedQuestion("When would I say this?", answer = "t1 is a greeting.")), viewModel.state.cardQuestions?.asked)
        assertTrue("The learner asks: When would I say this?" in translator.prompts.single())
        assertTrue("Spanish: t1" in translator.prompts.single())

        translator.completion = Completion("Only among friends.")
        viewModel.askAboutCard("Is it formal?")
        assertEquals(listOf("t1 is a greeting.", "Only among friends."), viewModel.state.cardQuestions?.asked?.map { it.answer })
        assertTrue("Learner: When would I say this?\nYou: t1 is a greeting." in translator.prompts.last())

        viewModel.askAboutCard("   ")
        assertEquals(2, translator.prompts.size)

        viewModel.dismissCardQuestions()
        assertEquals(null, viewModel.state.cardQuestions)
    }

    @Test
    fun aQuestionThatCannotBeAnsweredSaysWhy() = runTest {
        flashcards.seed(islandId = 1, cardCount = 1)
        val viewModel = createViewModel()

        // No API key yet: the sheet says so instead of offering a field.
        viewModel.openCardQuestions()
        assertTrue(viewModel.state.cardQuestions?.unavailable.orEmpty().contains("API key"))
        viewModel.askAboutCard("Why?")
        assertTrue(translator.prompts.isEmpty())

        settings.apiKey.value = "key"
        translator.completion = Completion.failure("No connection.")
        viewModel.openCardQuestions()
        viewModel.askAboutCard("Why?")
        assertEquals(listOf(AskedQuestion("Why?", error = "No connection.")), viewModel.state.cardQuestions?.asked)

        // The next question can be asked right after one that failed.
        translator.completion = Completion("Because.")
        viewModel.askAboutCard("Why?")
        assertEquals(listOf(null, "Because."), viewModel.state.cardQuestions?.asked?.map { it.answer })
    }

    @Test
    fun onDeviceTranslationCannotBeAskedAboutACard() = runTest {
        flashcards.seed(islandId = 1, cardCount = 1)
        settings.translationProvider.value = TranslationProvider.OnDevice
        val viewModel = createViewModel()

        viewModel.openCardQuestions()

        assertTrue(viewModel.state.cardQuestions?.unavailable.orEmpty().contains("On-device"))
    }

    @Test
    fun aQuestionIsSentWithTheCardsAlternativesItsNoteAndTheCoastsRegion() = runTest {
        flashcards.seed(islandId = 1, cardCount = 1)
        flashcards.setCoastGoal(1, null, "Mexico")
        flashcards.editCard(1, CardEdit("How are you?", "¿Cómo estás?", listOf("¿Qué tal?"), "Qué tal is casual.", 1))
        settings.apiKey.value = "key"
        val viewModel = createViewModel()

        viewModel.openCardQuestions()
        viewModel.askAboutCard("Is it formal?")

        val prompt = translator.prompts.single()
        assertTrue("English: How are you?" in prompt)
        assertTrue("Spanish: ¿Cómo estás?" in prompt)
        assertTrue("¿Qué tal?" in prompt)
        assertTrue("Note on the card: Qué tal is casual." in prompt)
        assertTrue("Spanish in Mexico" in prompt)
    }

    @Test
    fun aQuestionThatGotNoAnswerIsNotPassedOnWithTheNextOne() = runTest {
        flashcards.seed(islandId = 1, cardCount = 1)
        settings.apiKey.value = "key"
        translator.completion = Completion.failure("No connection.")
        val viewModel = createViewModel()

        viewModel.openCardQuestions()
        viewModel.askAboutCard("First?")
        translator.completion = Completion("An answer.")
        viewModel.askAboutCard("Second?")

        assertFalse("Earlier in this conversation" in translator.prompts.last())
        assertFalse("First?" in translator.prompts.last())
        assertEquals(listOf("First?", "Second?"), viewModel.state.cardQuestions?.asked?.map { it.question })
    }

    @Test
    fun anEmptyAnswerCountsAsNoAnswer() = runTest {
        flashcards.seed(islandId = 1, cardCount = 1)
        settings.apiKey.value = "key"
        translator.completion = Completion(" \n``` ")
        val viewModel = createViewModel()

        viewModel.openCardQuestions()
        viewModel.askAboutCard("Why?")

        assertEquals(listOf(AskedQuestion("Why?", error = "No answer.")), viewModel.state.cardQuestions?.asked)
    }

    @Test
    fun questionsStayWithTheCardTheyWereOpenedOn() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        settings.apiKey.value = "key"
        val viewModel = createViewModel()

        viewModel.openCardQuestions()
        viewModel.next()
        viewModel.askAboutCard("Why?")

        assertEquals("n2", viewModel.currentText)
        assertTrue("Spanish: t1" in translator.prompts.single())
        assertFalse("t2" in translator.prompts.single())
    }

    @Test
    fun openingTheQuestionsAgainStartsWithNoneAsked() = runTest {
        flashcards.seed(islandId = 1, cardCount = 1)
        settings.apiKey.value = "key"
        val viewModel = createViewModel()

        viewModel.openCardQuestions()
        viewModel.askAboutCard("Why?")
        viewModel.dismissCardQuestions()
        viewModel.openCardQuestions()

        assertTrue(viewModel.state.cardQuestions!!.asked.isEmpty())
        viewModel.askAboutCard("And now?")
        assertFalse("Earlier in this conversation" in translator.prompts.last())
    }

    @Test
    fun nothingIsAskedWithoutACardOrWithoutTheSheet() = runTest {
        settings.apiKey.value = "key"
        val empty = createViewModel()
        empty.openCardQuestions()
        assertEquals(null, empty.state.cardQuestions)

        flashcards.seed(islandId = 1, cardCount = 1)
        val viewModel = createViewModel()
        viewModel.askAboutCard("Why?")
        assertEquals(null, viewModel.state.cardQuestions)
        assertTrue(translator.prompts.isEmpty())
    }

    @Test
    fun aQuestionAboutACardThatWasDeletedMeanwhileIsNotSent() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        settings.apiKey.value = "key"
        val viewModel = createViewModel()

        viewModel.openCardQuestions()
        viewModel.deleteCurrentCard {}
        viewModel.askAboutCard("Why?")

        assertTrue(translator.prompts.isEmpty())
        assertTrue(viewModel.state.cardQuestions!!.asked.isEmpty())
    }

    /** A provider that answers only once [answer] is completed, to look at what happens while it is asked. */
    private class SlowTranslator : Translator {
        val answer = CompletableDeferred<Completion>()
        var asked = 0

        override suspend fun translateAndCategorize(request: TranslationRequest) = TranslationResult.FAILURE

        override suspend fun complete(engine: TranslationEngine, prompt: String): Completion {
            asked++
            return answer.await()
        }
    }

    private fun TestScope.createViewModel(translator: Translator): StudyViewModel {
        val viewModel = StudyViewModel(
            SavedStateHandle(mapOf("islandId" to 1, "islandName" to "Island 1", "scope" to "island", "coastId" to 0)),
            flashcards, settings, TEST_CLOCK, TEST_TIME_ZONE, speaker, translator
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    @Test
    fun oneQuestionIsAskedAtATimeAndShowsAsWaitingUntilItIsAnswered() = runTest {
        flashcards.seed(islandId = 1, cardCount = 1)
        settings.apiKey.value = "key"
        val slow = SlowTranslator()
        val viewModel = createViewModel(slow)

        viewModel.openCardQuestions()
        viewModel.askAboutCard("First?")
        assertTrue(viewModel.state.cardQuestions!!.isAsking)
        assertEquals(listOf(AskedQuestion("First?")), viewModel.state.cardQuestions?.asked)

        viewModel.askAboutCard("Second?")
        assertEquals(1, slow.asked)
        assertEquals(listOf("First?"), viewModel.state.cardQuestions?.asked?.map { it.question })

        slow.answer.complete(Completion("Because."))
        assertFalse(viewModel.state.cardQuestions!!.isAsking)
        assertEquals(listOf(AskedQuestion("First?", answer = "Because.")), viewModel.state.cardQuestions?.asked)
    }

    @Test
    fun anAnswerThatArrivesAfterTheSheetWasClosedIsDropped() = runTest {
        flashcards.seed(islandId = 1, cardCount = 1)
        settings.apiKey.value = "key"
        val slow = SlowTranslator()
        val viewModel = createViewModel(slow)

        viewModel.openCardQuestions()
        viewModel.askAboutCard("First?")
        viewModel.dismissCardQuestions()
        viewModel.openCardQuestions()
        slow.answer.complete(Completion("Too late."))

        assertTrue(viewModel.state.cardQuestions!!.asked.isEmpty())
    }

    @Test
    fun localAiCanBeAskedAboutACard() = runTest {
        flashcards.seed(islandId = 1, cardCount = 1)
        settings.translationProvider.value = TranslationProvider.LocalAi
        val viewModel = createViewModel()

        viewModel.openCardQuestions()
        val open = viewModel.state.cardQuestions!!

        // Either it is ready, or the sheet says what is missing (no model yet); it never just stays silent.
        if (open.unavailable == null) {
            viewModel.askAboutCard("Why?")
            assertEquals(listOf("A short explanation."), viewModel.state.cardQuestions?.asked?.map { it.answer })
        } else {
            assertTrue(open.unavailable!!.isNotBlank())
        }
        assertEquals("Local AI", open.providerLabel)
    }
}
