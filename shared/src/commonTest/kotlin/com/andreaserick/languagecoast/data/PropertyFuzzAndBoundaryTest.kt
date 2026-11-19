package com.andreaserick.languagecoast.data

import com.andreaserick.languagecoast.ui.study.answerMatches
import kotlinx.datetime.TimeZone
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Seeded property-based fuzz tests and pathological boundary value matrices (Spec 2.8):
 * FSRS scheduling invariants, Backup encode/decode round-trip, CardSearch & AnswerMatch Unicode/overflow handling.
 */
class PropertyFuzzAndBoundaryTest {

    @Test
    fun spacedRepetitionHoldsInvariantsAcross250SeededAndPathologicalInputs() {
        val seed = 20261119L
        val rng = Random(seed)
        val now = Instant.parse("2026-10-09T12:00:00Z")
        val pathologicalDoubles = listOf(
            Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
            0.0, -0.0, -1.0, -100.0, 0.0001, 1.0, 10.0, 36_500.0, 1e9
        )

        // First test explicit boundary matrix.
        for (s in pathologicalDoubles) {
            for (d in pathologicalDoubles) {
                val card = Flashcard(
                    cardId = 1, islandId = 1, nativeText = "a", targetText = "b",
                    stability = s, difficulty = d, lastReviewed = now.toEpochMilliseconds() - 86_400_000L
                )
                val outcomes = reviewOutcomes(card, now, TimeZone.UTC)
                assertFsrsInvariants(outcomes, "matrix s=$s d=$d")
            }
        }

        // Next run 250 seeded random review transitions.
        repeat(250) { iteration ->
            var card = Flashcard(cardId = iteration + 1, islandId = 1, nativeText = "n$iteration", targetText = "t$iteration")
            var currentNow = now
            repeat(8) { step ->
                val outcomes = reviewOutcomes(card, currentNow, TimeZone.UTC)
                assertFsrsInvariants(outcomes, "seed=$seed iter=$iteration step=$step")
                val pickedGrade = Grade.entries[rng.nextInt(Grade.entries.size)]
                card = outcomes.getValue(pickedGrade).card
                // Advance time anywhere from -2 days (clock skew) to +400 days.
                val deltaDays = rng.nextLong(-2L, 400L)
                currentNow = Instant.fromEpochMilliseconds(currentNow.toEpochMilliseconds() + deltaDays * 86_400_000L)
            }
        }
    }

    private fun assertFsrsInvariants(outcomes: Map<Grade, ReviewOutcome>, context: String) {
        assertEquals(Grade.entries.size, outcomes.size, context)
        outcomes.forEach { (grade, outcome) ->
            val s = outcome.card.stability!!
            val d = outcome.card.difficulty!!
            assertTrue(s.isFinite() && s > 0.0, "$context grade=$grade invalid stability=$s")
            assertTrue(d.isFinite() && d in 1.0..10.0, "$context grade=$grade invalid difficulty=$d")
            assertTrue(outcome.intervalDays in 1..36_500, "$context grade=$grade invalid interval=${outcome.intervalDays}")
        }
        val hard = outcomes.getValue(Grade.Hard).intervalDays
        val good = outcomes.getValue(Grade.Good).intervalDays
        val easy = outcomes.getValue(Grade.Easy).intervalDays
        assertTrue(hard <= good && good <= easy, "$context non-monotonic intervals: hard=$hard good=$good easy=$easy")
    }

    @Test
    fun backupRoundTripPreservesAllDataAcross100SeededFuzzCases() {
        val seed = 987654321L
        val rng = Random(seed)
        val trickyStrings = listOf(
            "",
            "   ",
            "مرحبا بالعالم", // Arabic RTL
            "שלום עולם", // Hebrew RTL
            "Family 👨‍👩‍👧‍👦 café naïve 日本語", // ZWJ emoji + accents + CJK
            "Line 1\nLine 2\r\n\"Quoted\" \\ slash",
            "<script>alert(1)</script> ' OR 1=1 --",
            "x".repeat(5_000)
        )

        repeat(100) { iter ->
            val lang = Languages.ALL[iter % Languages.ALL.size].name
            val cardCount = rng.nextInt(0, 6)
            val cards = List(cardCount) { idx ->
                BackupCard(
                    cardId = idx + 1,
                    islandId = 1,
                    nativeText = trickyStrings[rng.nextInt(trickyStrings.size)],
                    targetText = trickyStrings[rng.nextInt(trickyStrings.size)],
                    stability = if (rng.nextBoolean()) rng.nextDouble(0.1, 500.0) else null,
                    difficulty = if (rng.nextBoolean()) rng.nextDouble(1.0, 10.0) else null,
                    lastReviewed = if (rng.nextBoolean()) 1_760_000_000_000L else null,
                    due = if (rng.nextBoolean()) 1_760_086_400_000L else null,
                    lapses = rng.nextInt(0, 20),
                    alternatives = List(rng.nextInt(0, 3)) { trickyStrings[rng.nextInt(trickyStrings.size)] },
                    note = if (rng.nextBoolean()) trickyStrings[rng.nextInt(trickyStrings.size)] else null
                )
            }
            val backup = Backup(
                format = BACKUP_FORMAT,
                version = BACKUP_VERSION,
                createdAt = 1_760_000_000_000L + iter * 1_000L,
                coasts = listOf(BackupCoast(1, lang, creationDate = 1_760_000_000_000L, goal = trickyStrings[rng.nextInt(trickyStrings.size)])),
                islands = listOf(BackupIsland(1, 1, "Island $iter", creationDate = 1_760_000_000_000L)),
                cards = cards,
                settings = BackupSettings(nativeLanguage = "English", streakCount = rng.nextInt(0, 100))
            )

            val decoded = decodeBackup(encodeBackup(backup))
            assertEquals(backup, decoded, "Backup round-trip mismatch at seed=$seed iter=$iter")
        }
    }

    @Test
    fun searchAndAnswerMatchHandleUnicodeRtlEmojiAndMassiveStringsCleanly() {
        val huge = "word ".repeat(2_000) + "needle"
        val cards = listOf(
            Flashcard(cardId = 1, islandId = 1, nativeText = "Family 👨‍👩‍👧‍👦", targetText = "Familie"),
            Flashcard(cardId = 2, islandId = 1, nativeText = "مرحبا بالعالم", targetText = "שלום עולם"),
            Flashcard(cardId = 3, islandId = 1, nativeText = huge, targetText = "Nadel")
        )

        assertTrue(matchesSearch(cards[0], "family"))
        assertFalse(matchesSearch(cards[0], "👨‍👩‍👧‍👦")) // Pure-symbol query has no words per CardSearch contract and never crashes.
        assertTrue(matchesSearch(cards[1], "مرحبا"))
        assertTrue(matchesSearch(cards[1], "שלום"))
        assertTrue(matchesSearch(cards[2], "needle"))
        assertFalse(matchesSearch(cards[0], "   "))
        assertFalse(matchesSearch(cards[0], ""))

        assertTrue(answerMatches("مرحبا بالعالم!", "مرحبا بالعالم"))
        assertTrue(answerMatches("  Familie.  ", "familie"))
        assertFalse(answerMatches("", "Familie"))
        assertFalse(answerMatches("   ", "Familie"))
    }
}
