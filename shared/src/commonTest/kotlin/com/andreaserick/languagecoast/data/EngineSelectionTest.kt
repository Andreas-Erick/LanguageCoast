package com.andreaserick.languagecoast.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Nothing is sent to a cloud provider before the user has allowed it: a saved key alone doesn't make its
 * engine ready. Everything that sends text asks [currentEngine] first, so this is where that is checked.
 */
class EngineSelectionTest {

    private val cloudProviders = TranslationProvider.entries.filter { it.isCloud }

    private suspend fun SettingsRepository.saveKey(provider: TranslationProvider, key: String) = when (provider) {
        TranslationProvider.Gemini -> setApiKey(key)
        TranslationProvider.OpenAI -> setOpenAiKey(key)
        TranslationProvider.Claude -> setClaudeKey(key)
        TranslationProvider.Groq -> setGroqKey(key)
        TranslationProvider.DeepSeek -> setDeepSeekKey(key)
        TranslationProvider.OpenRouter -> setOpenRouterKey(key)
        TranslationProvider.OnDevice, TranslationProvider.LocalAi -> Unit
    }

    @Test
    fun aCloudProviderWithoutAKeyAsksForOne() = runTest {
        val settings = memorySettings()

        assertEquals(EngineChoice.Missing("Gemini needs an API key. Add one in Settings."), settings.currentEngine(TranslationProvider.Gemini))
        // The permission alone doesn't help either.
        settings.allowAiSharing(TranslationProvider.OpenRouter)
        assertEquals(EngineChoice.Missing("OpenRouter needs an API key. Add one in Settings."), settings.currentEngine(TranslationProvider.OpenRouter))
    }

    @Test
    fun aSavedKeyAloneIsNotPermissionToSendText() = runTest {
        cloudProviders.forEach { provider ->
            val settings = memorySettings()
            settings.saveKey(provider, "key")

            assertEquals(
                EngineChoice.Missing("Allow Language Coast to send text to ${provider.label} first. You can do that under Settings."),
                settings.currentEngine(provider),
                provider.name
            )
            // Nothing stands in for it either: the fallback asks the same question.
            assertNull(settings.fallbackCloudEngine(), provider.name)
        }
    }

    @Test
    fun aKeyAndThePermissionMakeTheEngineReady() = runTest {
        val settings = memorySettings()
        settings.setClaudeKey("sk-test")
        settings.allowAiSharing(TranslationProvider.Claude)

        val engine = TranslationEngine.Claude("sk-test", SettingsDefaults.CLAUDE_MODEL)
        assertEquals(EngineChoice.Ready(engine), settings.currentEngine(TranslationProvider.Claude))
        assertEquals(TranslationProvider.Claude to engine, settings.fallbackCloudEngine())
    }

    @Test
    fun thePermissionIsGivenPerProvider() = runTest {
        val settings = memorySettings()
        settings.setClaudeKey("sk-test")
        settings.setOpenAiKey("sk-other")

        settings.allowAiSharing(TranslationProvider.Claude)

        assertEquals(setOf("Claude"), settings.aiSharingAllowed.first())
        assertTrue(settings.currentEngine(TranslationProvider.Claude) is EngineChoice.Ready)
        assertTrue(settings.currentEngine(TranslationProvider.OpenAI) is EngineChoice.Missing)
    }

    @Test
    fun providersOnThePhoneNeedNeitherKeyNorPermission() = runTest {
        val settings = memorySettings()

        assertEquals(EngineChoice.Ready(TranslationEngine.OnDevice), settings.currentEngine(TranslationProvider.OnDevice))
        assertEquals(
            EngineChoice.Ready(TranslationEngine.LocalAi(modelId = SettingsDefaults.LOCAL_AI_MODEL)),
            settings.currentEngine(TranslationProvider.LocalAi)
        )
        // They send nothing anywhere, so there is nothing to allow.
        settings.allowAiSharing(TranslationProvider.OnDevice)
        settings.allowAiSharing(TranslationProvider.LocalAi)
        assertTrue(settings.aiSharingAllowed.first().isEmpty())
    }

    @Test
    fun keyOfGivesEachProviderItsOwnKey() = runTest {
        val settings = memorySettings()
        cloudProviders.forEach { settings.saveKey(it, "key of ${it.name}") }

        cloudProviders.forEach { assertEquals("key of ${it.name}", settings.keyOf(it).first(), it.name) }
        assertEquals("", settings.keyOf(TranslationProvider.OnDevice).first())
        assertEquals("", settings.keyOf(TranslationProvider.LocalAi).first())
    }

    @Test
    fun engineReadyAndTheProviderAwaitingPermissionFollowTheKeyAndThePermission() = runTest {
        val settings = memorySettings()
        settings.setTranslationProvider(TranslationProvider.Groq)

        // No key yet: not ready, and nothing to ask permission for.
        assertFalse(settings.engineReady().first())
        assertNull(settings.providerAwaitingPermission().first())

        settings.setGroqKey("gsk-test")
        assertFalse(settings.engineReady().first())
        assertEquals(TranslationProvider.Groq, settings.providerAwaitingPermission().first())

        // Another provider's permission doesn't count.
        settings.allowAiSharing(TranslationProvider.Gemini)
        assertFalse(settings.engineReady().first())
        assertEquals(TranslationProvider.Groq, settings.providerAwaitingPermission().first())

        settings.allowAiSharing(TranslationProvider.Groq)
        assertTrue(settings.engineReady().first())
        assertNull(settings.providerAwaitingPermission().first())

        // Picking a provider that isn't set up changes the answer.
        settings.setTranslationProvider(TranslationProvider.DeepSeek)
        assertFalse(settings.engineReady().first())
        assertNull(settings.providerAwaitingPermission().first())
    }

    @Test
    fun removingAKeyClearsItAndWithdrawsThePermission() = runTest {
        val settings = memorySettings()
        settings.setClaudeKey("sk-test")
        settings.setOpenAiKey("sk-other")
        settings.allowAiSharing(TranslationProvider.Claude)
        settings.allowAiSharing(TranslationProvider.OpenAI)

        settings.removeApiKey(TranslationProvider.Claude)

        assertEquals("", settings.claudeKey.first())
        assertEquals(setOf("OpenAI"), settings.aiSharingAllowed.first())
        // The other provider keeps its key and its permission.
        assertEquals("sk-other", settings.openAiKey.first())
        assertTrue(settings.currentEngine(TranslationProvider.OpenAI) is EngineChoice.Ready)

        // A key saved again later has to be allowed again.
        settings.setClaudeKey("sk-new")
        assertEquals(
            EngineChoice.Missing("Allow Language Coast to send text to Claude first. You can do that under Settings."),
            settings.currentEngine(TranslationProvider.Claude)
        )
    }

    @Test
    fun anEngineAJobStillHoldsIsTurnedAwayOnceItsKeyIsRemoved() = runTest {
        val settings = memorySettings()
        settings.setClaudeKey("sk-test")
        settings.allowAiSharing(TranslationProvider.Claude)
        // What a job that runs for minutes read when it began, and keeps using, key included.
        val engine = (settings.currentEngine(TranslationProvider.Claude) as EngineChoice.Ready).engine
        assertNull(settings.withdrawn(engine))

        settings.removeApiKey(TranslationProvider.Claude)

        assertEquals("Claude needs an API key. Add one in Settings.", settings.withdrawn(engine))
        // A key saved again is not the permission, so the job's engine still may not send.
        settings.setClaudeKey("sk-new")
        assertEquals("Allow Language Coast to send text to Claude first. You can do that under Settings.", settings.withdrawn(engine))
        // What runs on the phone sends nothing anywhere, so there is nothing to take back.
        assertNull(settings.withdrawn(TranslationEngine.OnDevice))
        assertNull(settings.withdrawn(TranslationEngine.LocalAi()))
    }

    @Test
    fun everyCloudEngineIsCheckedAgainstItsOwnProvider() = runTest {
        cloudProviders.forEach { provider ->
            val settings = memorySettings()
            // Every other provider is set up and allowed; only this one's key decides.
            cloudProviders.forEach {
                settings.saveKey(it, "key")
                settings.allowAiSharing(it)
            }
            val engine = (settings.currentEngine(provider) as EngineChoice.Ready).engine
            assertNull(settings.withdrawn(engine), provider.name)

            settings.removeApiKey(provider)

            assertEquals("${provider.label} needs an API key. Add one in Settings.", settings.withdrawn(engine), provider.name)
        }
    }

    @Test
    fun removingAKeyClearsTheKeyOfThatProviderOnly() = runTest {
        cloudProviders.forEach { removed ->
            val settings = memorySettings()
            cloudProviders.forEach { settings.saveKey(it, "key") }

            settings.removeApiKey(removed)

            cloudProviders.forEach { provider ->
                assertEquals(if (provider == removed) "" else "key", settings.keyOf(provider).first(), "${removed.name} removed, ${provider.name} read")
            }
        }
    }

    @Test
    fun savingABlankKeyRemovesItAndThePermission() = runTest {
        val settings = memorySettings()
        settings.setApiKey("AIza-test")
        settings.allowAiSharing(TranslationProvider.Gemini)

        settings.setApiKey(" ")

        assertEquals("", settings.apiKey.first())
        assertTrue(settings.aiSharingAllowed.first().isEmpty())
    }

    @Test
    fun removingTheLastThingStoredLeavesTheSettingsEmpty() = runTest {
        val settings = memorySettings()
        assertTrue(settings.isEmpty())

        settings.setApiKey("AIza-test")
        settings.allowAiSharing(TranslationProvider.Gemini)
        assertFalse(settings.isEmpty())

        settings.removeApiKey(TranslationProvider.Gemini)
        assertTrue(settings.isEmpty())
    }

    @Test
    fun thePermissionStaysOnThePhoneThatGaveIt() = runTest {
        val settings = memorySettings()
        settings.setClaudeKey("sk-test")
        settings.allowAiSharing(TranslationProvider.Claude)

        // Restoring a backup replaces the settings, but neither the keys nor the permission that goes with them.
        settings.restoreSettings(BackupSettings(nativeLanguage = "Dutch"))

        assertEquals("Dutch", settings.nativeLanguage.first())
        assertEquals("sk-test", settings.claudeKey.first())
        assertEquals(setOf("Claude"), settings.aiSharingAllowed.first())
    }
}
