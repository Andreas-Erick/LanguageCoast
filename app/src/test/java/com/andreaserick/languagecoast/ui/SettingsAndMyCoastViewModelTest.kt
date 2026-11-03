package com.andreaserick.languagecoast.ui

import androidx.lifecycle.SavedStateHandle
import com.andreaserick.languagecoast.data.Completion
import com.andreaserick.languagecoast.data.DeletedContent
import com.andreaserick.languagecoast.data.PhraseBank
import com.andreaserick.languagecoast.data.ReminderSettings
import com.andreaserick.languagecoast.data.StarterImporter
import com.andreaserick.languagecoast.data.StarterTranslator
import com.andreaserick.languagecoast.data.SettingsDefaults
import com.andreaserick.languagecoast.data.TranslationProvider
import com.andreaserick.languagecoast.testing.FakeFlashcardRepository
import com.andreaserick.languagecoast.testing.FakeReminderScheduler
import com.andreaserick.languagecoast.testing.FakeSettingsRepository
import com.andreaserick.languagecoast.testing.FakeTranslator
import com.andreaserick.languagecoast.testing.MainDispatcherRule
import com.andreaserick.languagecoast.testing.TEST_CLOCK
import com.andreaserick.languagecoast.testing.TEST_TIME_ZONE
import com.andreaserick.languagecoast.ui.coast.CoastViewModel
import com.andreaserick.languagecoast.ui.mycoast.MyCoastViewModel
import com.andreaserick.languagecoast.ui.settings.SettingsViewModel
import com.andreaserick.languagecoast.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsAndMyCoastViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val flashcards = FakeFlashcardRepository()
    private val settings = FakeSettingsRepository()
    private val scheduler = FakeReminderScheduler()
    private val translator = FakeTranslator()
    private val importer by lazy { StarterImporter(flashcards, settings, StarterTranslator(translator), CoroutineScope(Job())) }

    private fun coastViewModel(coastId: Int) =
        CoastViewModel(SavedStateHandle(mapOf("coastId" to coastId, "coastName" to "")), flashcards, settings, TEST_CLOCK, TEST_TIME_ZONE, importer)

    @Test
    fun savingApiKeyTrimsItAndShowsConfirmationUntilEdited() = runTest {
        val viewModel = SettingsViewModel(settings, flashcards, scheduler)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.saveApiKey("  my-key  ")

        assertEquals("my-key", settings.apiKey.value)
        assertEquals("my-key", viewModel.uiState.value.savedApiKey)
        assertTrue(viewModel.uiState.value.apiKeySaved)

        viewModel.onApiKeyEdited()
        assertFalse(viewModel.uiState.value.apiKeySaved)
    }

    @Test
    fun providerAndOpenRouterSettingsArePersistedSeparatelyFromGemini() = runTest {
        val viewModel = SettingsViewModel(settings, flashcards, scheduler)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.setTranslationProvider(TranslationProvider.OpenRouter)
        viewModel.setOpenRouterModel("anthropic/claude-haiku")
        viewModel.saveOpenRouterKey(" sk-or-key ")

        val state = viewModel.uiState.value
        assertEquals(TranslationProvider.OpenRouter, state.translationProvider)
        assertEquals("anthropic/claude-haiku", state.openRouterModel)
        assertEquals("sk-or-key", state.savedOpenRouterKey)
        assertTrue(state.openRouterSaved)
        assertFalse(state.apiKeySaved)
        assertEquals("", settings.apiKey.value)

        viewModel.onOpenRouterKeyEdited()
        assertFalse(viewModel.uiState.value.openRouterSaved)
    }

    @Test
    fun languageAndModelSelectionsArePersisted() = runTest {
        val viewModel = SettingsViewModel(settings, flashcards, scheduler)

        viewModel.setNativeLanguage("German")
        viewModel.setGeminiModel("gemini-3.5-flash")

        assertEquals("German", settings.nativeLanguage.value)
        assertEquals("gemini-3.5-flash", settings.geminiModel.value)
    }

    @Test
    fun nativeLanguageOptionsExcludeCoastLanguages() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        val viewModel = SettingsViewModel(settings, flashcards, scheduler)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val options = viewModel.uiState.value.nativeLanguageOptions.map { it.name }
        assertFalse("German" in options)
        assertTrue("English" in options)
    }

    @Test
    fun nativeLanguageCannotBeSetToACoastLanguage() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        val viewModel = SettingsViewModel(settings, flashcards, scheduler)

        viewModel.setNativeLanguage("German")

        assertEquals(SettingsDefaults.NATIVE_LANGUAGE, settings.nativeLanguage.value)
    }

    @Test
    fun myCoastRefreshesStreakAndListsCoastsWithCounts() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        flashcards.seed(islandId = 1, cardCount = 2, coastId = 1)
        flashcards.seed(islandId = 2, cardCount = 3, coastId = 1)
        flashcards.seedCoast(coastId = 2, language = "Icelandic")
        settings.streakCount.value = 3

        val viewModel = MyCoastViewModel(flashcards, settings, TEST_CLOCK, TEST_TIME_ZONE)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val state = viewModel.uiState.value
        assertEquals(1, settings.streakRefreshes)
        assertEquals(listOf("German Coast", "Icelandic Coast"), state.coasts.map { it.coast.displayName })
        assertEquals(listOf(2, 0), state.coasts.map { it.islandCount })
        assertEquals(listOf(5, 0), state.coasts.map { it.cardCount })
        assertEquals(3, state.streakCount)
        assertFalse(state.isLoading)
    }

    @Test
    fun coastSummaryCountsIslandsStudiedThisWeek() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        flashcards.seed(islandId = 1, cardCount = 1, coastId = 1)
        flashcards.seed(islandId = 2, cardCount = 1, coastId = 1)
        flashcards.seed(islandId = 3, cardCount = 1, coastId = 1)
        flashcards.markIslandStudied(1, TEST_CLOCK.now().toEpochMilliseconds())
        flashcards.markIslandStudied(2, (TEST_CLOCK.now() - 10.days).toEpochMilliseconds())

        val viewModel = MyCoastViewModel(flashcards, settings, TEST_CLOCK, TEST_TIME_ZONE)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val summary = viewModel.uiState.value.coasts.single()
        assertEquals(1, summary.islandsStudiedRecently)
        assertEquals(TEST_CLOCK.now().toEpochMilliseconds(), summary.lastStudied)
        assertEquals(LocalDate(2026, 10, 9), viewModel.uiState.value.today)
    }

    @Test
    fun deletedCoastCanBeRestoredWithItsIslandsAndCards() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        flashcards.seed(islandId = 1, cardCount = 2, coastId = 1)
        val viewModel = MyCoastViewModel(flashcards, settings, TEST_CLOCK, TEST_TIME_ZONE)
        var deleted: DeletedContent? = null

        viewModel.deleteCoast(flashcards.coasts.value.single()) { deleted = it }
        assertTrue(flashcards.coasts.value.isEmpty())
        assertTrue(flashcards.cards.value.isEmpty())

        viewModel.restore(deleted!!)
        assertEquals(listOf("German"), flashcards.coasts.value.map { it.language })
        assertEquals(listOf("Island 1"), flashcards.islands.value.map { it.name })
        assertEquals(2, flashcards.cards.value.size)
    }

    @Test
    fun changingReminderSettingsReschedules() = runTest {
        val viewModel = SettingsViewModel(settings, flashcards, scheduler)

        viewModel.setReminderTime(LocalTime(7, 30))
        assertEquals(ReminderSettings(enabled = true, time = LocalTime(7, 30)), settings.reminderSettings.value)

        viewModel.setRemindersEnabled(false)
        assertEquals(ReminderSettings(enabled = false, time = LocalTime(7, 30)), settings.reminderSettings.value)
        assertEquals(2, scheduler.reschedules)
    }

    @Test
    fun newCoastLanguagesExcludeNativeLanguageAndExistingCoasts() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        val viewModel = MyCoastViewModel(flashcards, settings, TEST_CLOCK, TEST_TIME_ZONE)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val available = viewModel.uiState.value.availableLanguages.map { it.name }
        assertFalse(SettingsDefaults.NATIVE_LANGUAGE in available)
        assertFalse("German" in available)
        assertTrue("Icelandic" in available)
    }

    @Test
    fun addingCoastMakesItActive() = runTest {
        val viewModel = MyCoastViewModel(flashcards, settings, TEST_CLOCK, TEST_TIME_ZONE)

        viewModel.addCoast("Norwegian")

        val coast = flashcards.coasts.value.single()
        assertEquals("Norwegian", coast.language)
        assertEquals(coast.coastId, settings.activeCoastId.value)
    }

    @Test
    fun deletingCoastRemovesItsIslandsAndCardsOnly() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        flashcards.seed(islandId = 1, cardCount = 2, coastId = 1)
        flashcards.seedCoast(coastId = 2, language = "Icelandic")
        flashcards.seed(islandId = 2, cardCount = 1, coastId = 2)
        val viewModel = MyCoastViewModel(flashcards, settings, TEST_CLOCK, TEST_TIME_ZONE)

        viewModel.deleteCoast(flashcards.coasts.value.first()) {}

        assertEquals(listOf("Icelandic"), flashcards.coasts.value.map { it.language })
        assertEquals(listOf(2), flashcards.islands.value.map { it.islandId })
        assertEquals(1, flashcards.cards.value.size)
    }

    @Test
    fun coastShowsOnlyItsOwnIslandsAndBecomesActive() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        flashcards.seed(islandId = 1, cardCount = 1, coastId = 1)
        flashcards.seedCoast(coastId = 2, language = "Icelandic")
        flashcards.seed(islandId = 2, cardCount = 1, coastId = 2)

        val viewModel = coastViewModel(coastId = 2)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertEquals("Icelandic Coast", viewModel.uiState.value.coastName)
        assertEquals(listOf("Island 2"), viewModel.uiState.value.islands.map { it.island.name })
        assertEquals(2, settings.activeCoastId.value)
    }

    @Test
    fun coastOffersEveryStarterTopicItDoesNotHaveYet() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        settings.markStarterTopicAdded(1, "Numbers")
        settings.markStarterTopicAdded(2, "Greetings") // Another coast's topics don't count.
        val viewModel = coastViewModel(coastId = 1)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val topics = viewModel.uiState.value.starterTopics
        assertEquals(PhraseBank.THEMES.size - 1, topics.size)
        assertTrue(topics.none { it.theme.category == "Numbers" })
        val aboutMe = topics.single { it.theme.category == "About Me" }
        assertEquals(listOf("name", "city", "country", "job"), aboutMe.profileBlanks)
        // "I'm [number] years old." and the like need a value per sentence, so a whole-topic import leaves them out.
        assertTrue(aboutMe.sentenceCount < aboutMe.theme.sentences.size)
        assertTrue(aboutMe.sentenceCount >= 10)
        assertEquals("German", viewModel.uiState.value.coastLanguage)
    }

    @Test
    fun addingAStarterTopicMakesAnIslandWithEverySentenceTranslated() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        settings.translationProvider.value = TranslationProvider.OnDevice
        val greetings = PhraseBank.theme("Greetings")!!

        importer.run(1, listOf("Greetings"), emptyMap())

        val island = flashcards.islands.value.single()
        assertEquals("Greetings", island.name)
        assertEquals("👋", island.emoji)
        assertEquals(greetings.sentences, flashcards.cards.value.map { it.nativeText })
        assertTrue(flashcards.cards.value.all { it.targetText == "Hola" })
        assertEquals(setOf("1:Greetings"), settings.starterTopicsAdded.value)
        val progress = importer.progress.value!!
        assertTrue(progress.isFinished)
        assertEquals(greetings.sentences.size, progress.added)
        assertEquals(0, progress.failed)
        assertTrue(translator.lastRequest?.context!!.startsWith("A phrasebook entry from the topic \"Greetings\""))
        assertEquals("Greetings", translator.lastRequest?.userCategory)
    }

    @Test
    fun addingAStarterTopicFillsInTheProfileAndSkipsWhatItCannot() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        settings.translationProvider.value = TranslationProvider.OnDevice
        flashcards.seed(islandId = 1, cardCount = 1, coastId = 1)
        flashcards.cards.value = flashcards.cards.value.map { it.copy(nativeText = "I'm here on holiday.") }

        importer.run(1, listOf("About Me"), mapOf("name" to "Anna", "city" to "Oslo"))

        val added = flashcards.cards.value.map { it.nativeText }
        assertTrue("My name is Anna." in added)
        assertTrue("I live in Oslo." in added)
        assertTrue(added.none { "[" in it }) // No blank left unfilled.
        assertTrue(added.none { it.startsWith("I'm from") }) // [country] wasn't given.
        assertTrue(added.none { it.startsWith("I work as") })
        assertEquals(1, added.count { it == "I'm here on holiday." }) // Already on the coast: not added twice.
    }

    @Test
    fun addingStarterTopicsWithTheModelTranslatesInBatches() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        settings.apiKey.value = "key"
        val colours = PhraseBank.theme("Colours")!!
        translator.completion = Completion(colours.sentences.mapIndexed { i, _ -> "${i + 1} || Farbe ${i + 1}" }.joinToString("\n"))

        importer.run(1, listOf("Colours"), emptyMap())

        assertEquals(colours.sentences.size, flashcards.cards.value.size)
        assertEquals("Farbe 1", flashcards.cards.value.first().targetText)
        assertEquals(colours.sentences.size / StarterTranslator.BATCH_SIZE + 1, translator.prompts.size)
        assertTrue(translator.prompts.first().contains("dictionary form"))
        assertEquals(0, translator.requests) // Nothing fell back to one-by-one translation.
    }

    @Test
    fun addingStarterTopicsWithoutAProviderReportsTheProblem() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        settings.translationProvider.value = TranslationProvider.Gemini
        settings.apiKey.value = ""

        importer.run(1, listOf("Greetings"), emptyMap())

        val progress = importer.progress.value!!
        assertTrue(progress.isFinished)
        assertTrue(progress.error!!.contains("Gemini"))
        assertTrue(flashcards.cards.value.isEmpty())
        assertTrue(settings.starterTopicsAdded.value.isEmpty())
    }

    @Test
    fun deletingIslandRemovesItAndItsCards() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        val viewModel = coastViewModel(coastId = 1)

        viewModel.deleteIsland(flashcards.islands.value.single()) {}

        assertTrue(flashcards.islands.value.isEmpty())
        assertTrue(flashcards.cards.value.isEmpty())
    }

    @Test
    fun myCoastCountsTheWeeklyGoalFreezeAndLeakyWords() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        flashcards.seed(islandId = 1, cardCount = 2, coastId = 1)
        flashcards.seed(islandId = 2, cardCount = 1, coastId = 1)
        // TEST_CLOCK is Friday 2026-10-09; Monday the 5th starts the week.
        flashcards.markIslandStudied(1, TEST_CLOCK.now().toEpochMilliseconds())
        flashcards.markIslandStudied(2, (TEST_CLOCK.now() - 6.days).toEpochMilliseconds())
        flashcards.cards.update { all -> all.map { if (it.islandId == 1 && it.nativeText == "n1") it.copy(lapses = 3) else it } }
        settings.weeklyGoal.value = 3
        settings.freezeUsedOn.value = LocalDate(2026, 10, 7)

        val viewModel = MyCoastViewModel(flashcards, settings, TEST_CLOCK, TEST_TIME_ZONE)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val state = viewModel.uiState.value
        assertEquals(3, state.weeklyGoal)
        assertEquals(1, state.islandsThisWeek)
        assertEquals(LocalDate(2026, 10, 7), state.freezeUsedOn)
        assertEquals(1, state.leakyCount)
    }

    @Test
    fun lookAndFeelAndGoalSettingsAreSaved() = runTest {
        val viewModel = SettingsViewModel(settings, flashcards, scheduler)

        viewModel.setThemeMode(ThemeMode.Light)
        viewModel.setHapticsEnabled(false)
        viewModel.setSoundsEnabled(false)
        viewModel.setWeeklyGoal(7)

        assertEquals("light", settings.themeMode.value)
        assertFalse(settings.hapticsEnabled.value)
        assertFalse(settings.soundsEnabled.value)
        assertEquals(7, settings.weeklyGoal.value)
    }
}
