package com.andreaserick.languagecoast.data

import com.andreaserick.languagecoast.testing.FakeFlashcardRepository
import com.andreaserick.languagecoast.testing.FakeReminderScheduler
import com.andreaserick.languagecoast.testing.FakeSettingsRepository
import com.andreaserick.languagecoast.testing.MainDispatcherRule
import com.andreaserick.languagecoast.ui.settings.SettingsViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CheckForNewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settings = FakeSettingsRepository()

    /** What Anthropic offers in this test: one model the app knows (as a dated copy) and three it doesn't. */
    private val claudeToday = ModelListing.Found(
        listOf(
            LiveModel("claude-opus-5-5", created = 400),
            LiveModel("claude-sonnet-5-5", created = 300),
            LiveModel("claude-haiku-5-5", created = 200),
            LiveModel("claude-haiku-4-5-20251001", created = 100)
        )
    )

    private val asked = mutableListOf<Pair<TranslationProvider, String>>()
    private fun lister(answer: ModelListing) = ModelLister { provider, key ->
        asked += provider to key
        answer
    }

    @Test
    fun newModelsAreAddedAndRetiredOnesRemoved() = runTest {
        settings.claudeKey.value = "sk-ant-key"

        val outcome = checkForNewModels(TranslationProvider.Claude, settings, lister(claudeToday))

        assertEquals(listOf(TranslationProvider.Claude to "sk-ant-key"), asked)
        assertFalse(outcome.failed)
        assertEquals("Added 3 models and removed 3 models that Claude no longer offers.", outcome.message)
        assertEquals(
            listOf("claude-haiku-4-5", "claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5"),
            settings.modelOptions(TranslationProvider.Claude).first()
        )
        // The model in use is still offered, so it stays.
        assertEquals("claude-haiku-4-5", settings.claudeModel.value)
        // The other providers keep their lists.
        assertEquals(SettingsDefaults.OPENAI_MODELS, settings.modelOptions(TranslationProvider.OpenAI).first())

        // A second check finds nothing to do.
        assertEquals("Your Claude models are up to date.", checkForNewModels(TranslationProvider.Claude, settings, lister(claudeToday)).message)
    }

    @Test
    fun aModelInUseThatIsGoneIsReplacedAndTheUserIsTold() = runTest {
        settings.claudeKey.value = "sk-ant-key"
        settings.claudeModel.value = "claude-3-7-sonnet-latest"

        val outcome = checkForNewModels(TranslationProvider.Claude, settings, lister(claudeToday))

        assertEquals("claude-haiku-4-5", settings.claudeModel.value)
        assertTrue("Your model claude-3-7-sonnet-latest is gone, so claude-haiku-4-5 is selected now." in outcome.message)
    }

    @Test
    fun aCheckThatFailsChangesNothing() = runTest {
        // No key saved: the lister gets an empty key, which the app's lister turns away before it touches the network.
        val noKey = checkForNewModels(TranslationProvider.Claude, settings, turnsAnEmptyKeyAway)
        assertTrue(noKey.failed)
        assertEquals("Save your Claude API key first, then check again.", noKey.message)

        settings.claudeKey.value = "sk-ant-key"
        val refused = checkForNewModels(TranslationProvider.Claude, settings, lister(ModelListing.Failed("Claude didn't accept the API key. Check your key and try again.")))
        assertTrue(refused.failed)
        val empty = checkForNewModels(TranslationProvider.Claude, settings, lister(ModelListing.Found(emptyList())))
        assertTrue(empty.failed)

        assertEquals(SettingsDefaults.CLAUDE_MODELS, settings.modelOptions(TranslationProvider.Claude).first())
        assertEquals(SettingsDefaults.CLAUDE_MODEL, settings.claudeModel.value)

        // A provider whose model isn't picked from a list has nothing to check.
        assertTrue(checkForNewModels(TranslationProvider.OpenRouter, settings, lister(claudeToday)).failed)
    }

    @Test
    fun eachProviderIsCheckedWithItsOwnKeyAndModel() = runTest {
        settings.apiKey.value = "gemini-key"
        settings.groqKey.value = "groq-key"
        settings.groqModel.value = "gemma2-9b-it"

        checkForNewModels(TranslationProvider.Gemini, settings, lister(ModelListing.Found(listOf(LiveModel("gemini-3.5-flash-lite"), LiveModel("gemini-5-flash")))))
        val groq = checkForNewModels(TranslationProvider.Groq, settings, lister(ModelListing.Found(listOf(LiveModel("llama-3.3-70b-versatile"), LiveModel("llama-3.1-8b-instant")))))

        assertEquals(listOf(TranslationProvider.Gemini to "gemini-key", TranslationProvider.Groq to "groq-key"), asked)
        assertEquals(listOf("gemini-3.5-flash-lite", "gemini-5-flash"), settings.modelOptions(TranslationProvider.Gemini).first())
        assertEquals(SettingsDefaults.GEMINI_MODEL, settings.geminiModel.value)
        assertEquals("llama-3.3-70b-versatile", settings.groqModel.value)
        assertEquals("Removed 2 models that Groq no longer offers. Your model gemma2-9b-it is gone, so llama-3.3-70b-versatile is selected now.", groq.message)
    }

    @Test
    fun settingsCheckTheProviderInUseAndShowWhatCameOfIt() = runTest {
        settings.translationProvider.value = TranslationProvider.Claude
        settings.claudeKey.value = "sk-ant-key"
        val answer = CompletableDeferred<ModelListing>()
        val viewModel = SettingsViewModel(
            settings, FakeFlashcardRepository(), FakeReminderScheduler(),
            modelLister = ModelLister { provider, key ->
                asked += provider to key
                answer.await()
            }
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        assertEquals(SettingsDefaults.CLAUDE_MODELS, viewModel.uiState.value.modelOptions[TranslationProvider.Claude])

        viewModel.checkForNewModels()
        // While Claude is being asked, a second tap does nothing.
        viewModel.checkForNewModels()
        assertEquals(TranslationProvider.Claude, viewModel.uiState.value.modelCheck.checking)
        assertEquals(1, asked.size)

        answer.complete(claudeToday)

        val state = viewModel.uiState.value
        assertEquals(null, state.modelCheck.checking)
        assertEquals(TranslationProvider.Claude, state.modelCheck.provider)
        assertEquals("Added 3 models and removed 3 models that Claude no longer offers.", state.modelCheck.message)
        assertFalse(state.modelCheck.failed)
        assertEquals(listOf("claude-haiku-4-5", "claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5"), state.modelOptions[TranslationProvider.Claude])
        assertEquals("claude-haiku-4-5", state.claudeModel)
    }

    /** Stands in for [ProviderModelLister] where no key is saved: the same message, and no network. */
    private val turnsAnEmptyKeyAway = ModelLister { provider, key ->
        if (key.isBlank()) ModelListing.Failed("Save your ${provider.label} API key first, then check again.") else error("no network in tests")
    }
}
