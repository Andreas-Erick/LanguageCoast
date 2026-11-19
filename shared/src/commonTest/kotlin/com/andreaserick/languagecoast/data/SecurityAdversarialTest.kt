package com.andreaserick.languagecoast.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeSource

/**
 * Automated adversarial security & boundary tests (Spec 2.9):
 * CSV formula injection, Markdown XSS, deeply nested JSON StackOverflow DoS, ReDoS timing,
 * Gemini URL path/query injection, log secret scrubbing, SecretStore isolation, and corrupted backups.
 */
class SecurityAdversarialTest {

    private val coast = Coast(coastId = 1, language = "German")
    private val island = LanguageIsland(islandId = 1, coastId = 1, name = "Security", creationDate = 1_700_000_000_000L)

    @Test
    fun csvExportNeutralizesSpreadsheetFormulaInjectionPayloads() {
        val payloads = listOf(
            "=CMD|' /C calc'!A0",
            "+1+1",
            "-2+3",
            "@SUM(1+1)*cmd|' /C calc'!A0",
            "\t=1+1",
            "\r=1+1",
            "   =HYPERLINK(\"http://evil.example\")"
        )
        val cards = payloads.mapIndexed { idx, payload ->
            Flashcard(cardId = idx + 1, islandId = 1, nativeText = payload, targetText = "Safe", note = payload)
        }
        val csv = exportCards(listOf(CoastContent(coast, listOf(island), cards)), ExportFormat.Csv, "English")
        val dataRows = csv.removePrefix("\uFEFF").split("\r\n").drop(1).filter { it.isNotEmpty() }

        assertEquals(payloads.size, dataRows.size)
        dataRows.forEach { row ->
            val firstCell = row.substringBefore(",").removePrefix("\"")
            assertTrue(
                firstCell.startsWith("'"),
                "Expected formula cell to be neutralized with leading apostrophe, got: $row"
            )
        }
    }

    @Test
    fun markdownExportEscapesRawHtmlAndScriptPayloadsWhileKeepingLineBreaks() {
        val xssCard = Flashcard(
            cardId = 1,
            islandId = 1,
            nativeText = "<script>alert(1)</script>\n<img src=x onerror=alert(1)>",
            targetText = "<svg onload=alert(1)> | safe",
            note = "<a href=\"javascript:alert(1)\">click</a>"
        )
        val md = exportCards(listOf(CoastContent(coast, listOf(island), listOf(xssCard))), ExportFormat.Markdown, "English")

        assertFalse("<script>" in md, "Raw <script> tag must be escaped in Markdown export")
        assertFalse("<img " in md, "Raw <img> tag must be escaped in Markdown export")
        assertFalse("<svg " in md, "Raw <svg> tag must be escaped in Markdown export")
        assertTrue("&lt;script&gt;alert(1)&lt;/script&gt;<br>&lt;img src=x onerror=alert(1)&gt;" in md)
        assertTrue("&lt;svg onload=alert(1)&gt; \\| safe" in md)
    }

    @Test
    fun deeplyNestedJsonPayloadsAreRejectedWithoutStackOverflow() {
        val deepObjects = "{\"a\":".repeat(2_000) + "1" + "}".repeat(2_000)
        val deepArrays = "[".repeat(2_000) + "]".repeat(2_000)

        assertFailsWith<BackupException> { decodeBackup(deepObjects) }
        assertFailsWith<BackupException> { decodeBackup(deepArrays) }
        assertNull(SettingsSnapshot.decode(deepObjects))
        assertNull(SettingsSnapshot.decode(deepArrays))
        assertEquals(emptyMap(), ankiDeckNames(deepObjects))
        assertEquals(emptyMap(), ankiDeckNames(deepArrays))
    }

    @Test
    fun ankiFieldTextResistsReDoSOnPathologicalUnclosedTagsAndCloze() {
        val unclosedStyles = "<style>".repeat(20_000) + "hello"
        val unclosedScripts = "<script type=\"text/javascript\">".repeat(20_000) + "world"
        val pathologicalCloze = "{{c1::" + "a::".repeat(20_000) + "end"
        val repeatedUnclosedCloze = "{{c1::".repeat(20_000) + "tail"

        val start = TimeSource.Monotonic.markNow()
        val s1 = ankiFieldText(unclosedStyles)
        val s2 = ankiFieldText(unclosedScripts)
        val s3 = ankiFieldText(pathologicalCloze)
        val s4 = ankiFieldText(repeatedUnclosedCloze)
        val elapsed = start.elapsedNow()

        assertTrue(elapsed < 2.seconds, "ankiFieldText took $elapsed on adversarial input (possible ReDoS)")
        assertTrue(s1.endsWith("hello"))
        assertTrue(s2.endsWith("world"))
        assertTrue(s3.contains("end"))
        assertTrue(s4.endsWith("tail"))
    }

    @Test
    fun geminiModelNormalizationBlocksPathTraversalAndQueryInjection() {
        val adversarialModels = listOf(
            "../v1beta/other",
            "../../etc/passwd",
            "gemini-3-flash?key=stolen",
            "gemini-3-flash#fragment",
            "gemini-3-flash\r\nX-Injected: true",
            "gemini-3-flash/extra",
            "gemini..flash",
            "   "
        )
        adversarialModels.forEach { bad ->
            assertEquals(
                SettingsDefaults.GEMINI_MODEL,
                normalizeGeminiModel(bad),
                "Adversarial Gemini model '$bad' must fall back to default"
            )
        }
        assertEquals("gemini-3.5-flash-preview", normalizeGeminiModel("models/gemini-3.5-flash-preview"))
        assertEquals("gemini-4.0-pro", normalizeGeminiModel("gemini-4.0-pro"))
    }

    @Test
    fun logSecretRedactionScrubsAllSupportedProviderKeysAndHeaders() {
        val samples = listOf(
            "AIzaSyD-1234567890abcdefGHIJKLMN" to "Gemini key",
            "sk-1234567890abcdefGHIJKLMN" to "OpenAI key",
            "sk-proj-abcdef1234567890XYZ" to "OpenAI project key",
            "sk-ant-api03-abcdef1234567890" to "Anthropic key",
            "sk-or-v1-abcdef1234567890" to "OpenRouter key",
            "gsk_abcdef1234567890XYZ" to "Groq key",
            "hf_abcdef1234567890XYZ" to "Hugging Face token"
        )
        samples.forEach { (secret, label) ->
            val msg = "Request failed Authorization: Bearer $secret x-goog-api-key=$secret ?key=$secret raw=$secret"
            val scrubbed = redactSecrets(msg)
            assertFalse(secret in scrubbed, "$label leaked into scrubbed log: $scrubbed")
            assertTrue("[REDACTED]" in scrubbed)
        }
    }

    @Test
    fun apiKeysStayInSecretStoreAndLegacyPlaintextKeysMigrateOutOfDataStore() = runTest {
        val store = MemoryDataStore()
        val secretStore = InMemorySecretStore()
        val clock = object : Clock {
            override fun now() = Instant.parse("2026-10-09T12:00:00Z")
        }
        // Simulate an older app version that had stored plaintext keys in DataStore.
        store.edit { prefs ->
            prefs[stringPreferencesKey("gemini_api_key")] = "AIzaLegacyKey1234567890"
            prefs[stringPreferencesKey("hugging_face_token")] = "hf_LegacyToken12345678"
        }

        val repo = DataStoreSettingsRepository(store, clock, TimeZone.UTC, secretStore)
        // Readable immediately even before migration.
        assertEquals("AIzaLegacyKey1234567890", repo.apiKey.first())
        assertEquals("hf_LegacyToken12345678", repo.huggingFaceToken.first())

        repo.migrateLegacySecrets()

        // Moved to hardware-backed SecretStore and purged from plaintext DataStore.
        assertEquals("AIzaLegacyKey1234567890", secretStore.get("gemini_api_key"))
        assertEquals("hf_LegacyToken12345678", secretStore.get("hugging_face_token"))
        assertNull(store.data.first()[stringPreferencesKey("gemini_api_key")])
        assertNull(store.data.first()[stringPreferencesKey("hugging_face_token")])

        // New keys go straight to SecretStore, never DataStore.
        repo.setClaudeKey("sk-ant-NewSecret1234567890")
        assertEquals("sk-ant-NewSecret1234567890", repo.claudeKey.first())
        assertNull(store.data.first()[stringPreferencesKey("claude_api_key")])
    }

    @Test
    fun decodeBackupRejectsInvalidVersionTimestampsAndCorruptedFsrsValues() {
        val valid = Backup(
            format = BACKUP_FORMAT,
            version = BACKUP_VERSION,
            createdAt = 1_760_000_000_000L,
            coasts = listOf(BackupCoast(1, "German", creationDate = 1_760_000_000_000L)),
            islands = listOf(BackupIsland(1, 1, "Greetings", creationDate = 1_760_000_000_000L)),
            cards = listOf(BackupCard(1, 1, "Hello", "Hallo", stability = 2.5, difficulty = 5.0, lapses = 0)),
            settings = BackupSettings()
        )
        // Baseline valid backup decodes cleanly.
        assertEquals(1, decodeBackup(encodeBackup(valid)).cards.size)

        val corruptedCases = listOf(
            valid.copy(version = 0),
            valid.copy(version = -1),
            valid.copy(createdAt = Long.MAX_VALUE),
            valid.copy(createdAt = -10L),
            valid.copy(cards = listOf(valid.cards[0].copy(stability = 0.0))),
            valid.copy(cards = listOf(valid.cards[0].copy(stability = -2.0))),
            valid.copy(cards = listOf(valid.cards[0].copy(difficulty = 0.5))),
            valid.copy(cards = listOf(valid.cards[0].copy(difficulty = 11.0))),
            valid.copy(cards = listOf(valid.cards[0].copy(lapses = -1))),
            valid.copy(cards = listOf(valid.cards[0].copy(due = Long.MAX_VALUE)))
        )
        corruptedCases.forEach { bad ->
            assertFailsWith<BackupException>("Expected BackupException for corrupted backup: $bad") {
                decodeBackup(encodeBackup(bad))
            }
        }
    }
}
