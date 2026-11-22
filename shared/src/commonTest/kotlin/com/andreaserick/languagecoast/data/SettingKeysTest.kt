package com.andreaserick.languagecoast.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingKeysTest {

    /** A backup with every field away from its default, so a field that isn't carried shows up as a difference. */
    private val everything = BackupSettings(
        nativeLanguage = "German",
        activeCoastId = 3,
        translationProvider = TranslationProvider.Claude.name,
        geminiModel = "gemini-3.1-pro-preview",
        openRouterModel = "openrouter/auto",
        localAiModel = "qwen3-0.6b",
        openAiModel = "gpt-5",
        claudeModel = "claude-fable-5-2",
        groqModel = "qwen3-32b",
        deepSeekModel = "deepseek-chat",
        streakCount = 12,
        lastStudyDate = "2026-10-08",
        studyDays = listOf("2026-10-07", "2026-10-08"),
        reminderEnabled = false,
        reminderMinuteOfDay = 8 * 60 + 30,
        studyReversed = true,
        weeklyGoal = 9,
        freezeUsedOn = "2026-10-01",
        themeMode = "dark",
        hapticsEnabled = false,
        soundsEnabled = false,
        starterTopicsAdded = listOf("1:Greetings", "1:Travel"),
        profile = Profile.PLACEHOLDERS.associateWith { "my $it" },
        personalAnswers = PersonalIslands.ANSWER_KEYS.take(3).associateWith { "answer to $it" },
        personalIslandsBuilt = mapOf("1:pets" to "abc123"),
        wording = Wording.entries.first { it != Wording.Unspecified }.key,
        pace = Pace.Sprint.key
    )

    @Test
    fun everyFieldOfABackupComesBackFromOne() = runTest {
        val settings = memorySettings()

        settings.restoreSettings(everything)

        assertEquals(everything, settings.backupSettings())
    }

    @Test
    fun aRestoreOfDefaultsLeavesNothingBehind() = runTest {
        val settings = memorySettings()
        settings.restoreSettings(everything)

        settings.restoreSettings(BackupSettings())

        assertEquals(BackupSettings(), settings.backupSettings())
    }

    @Test
    fun everyPreferenceIsDeclaredOnceAndLocationAndSecretsStayOnThePhone() = runTest {
        val names = SettingKeys.backedUp.map { it.key.name } + SettingKeys.deviceOnly.map { it.name }
        assertEquals(names.size, names.toSet().size, names.groupingBy { it }.eachCount().filterValues { it > 1 }.toString())

        val deviceOnly = SettingKeys.deviceOnly.map { it.name }.toSet()
        assertTrue(SettingKeys.SECRET_KEYS.all { it.name in deviceOnly })
        assertTrue(SettingKeys.SUN_LOCATION.name in deviceOnly && SettingKeys.SUN_USES_LOCATION.name in deviceOnly)
        assertTrue(SettingKeys.AI_SHARING_ALLOWED.name in deviceOnly)

        // And a location set on this phone isn't in its backup.
        val settings = memorySettings()
        settings.setSunUsesLocation(true)
        settings.setSunLocation(SunLocation(59.9, 10.8))
        settings.restoreSettings(settings.backupSettings())
        assertTrue(settings.sunUsesLocation.first())
        assertFalse(settings.backupSettings().toString().contains("59.9"))
    }
}
