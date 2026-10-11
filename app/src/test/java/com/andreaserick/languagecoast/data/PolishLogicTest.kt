package com.andreaserick.languagecoast.data

import com.andreaserick.languagecoast.notifications.RANDOM_WINDOW_END_HOUR
import com.andreaserick.languagecoast.notifications.RANDOM_WINDOW_START_HOUR
import com.andreaserick.languagecoast.notifications.delayUntilNextReminder
import com.andreaserick.languagecoast.ui.components.lastStudiedLabel
import com.andreaserick.languagecoast.ui.study.splitPunctuation
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toKotlinLocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.random.Random

class PolishLogicTest {

    // --- Island emoji ---

    @Test
    fun categoryEmojiMatchesKeywordsAtWordStarts() {
        assertEquals("🍽️", emojiForCategory("Restaurant"))
        assertEquals("👋", emojiForCategory("Greetings"))
        assertEquals("🆘", emojiForCategory("Asking for Help"))
        // "weather" contains "eat" and "restaurant" contains "art", but neither starts a word.
        assertEquals("🌤️", emojiForCategory("Weather"))
        assertEquals(DEFAULT_ISLAND_EMOJI, emojiForCategory("Zzyzx"))
    }

    @Test
    fun islandsKeepTheirOwnEmoji() {
        assertEquals("🐴", islandEmoji(LanguageIsland(coastId = 1, name = "Restaurant", emoji = "🐴")))
        assertEquals("🍽️", islandEmoji(LanguageIsland(coastId = 1, name = "Restaurant")))
    }

    // --- Gemini response emoji ---

    @Test
    fun parsesOptionalEmoji() {
        assertEquals("🐴", parseTranslationResponse("TRANSLATION: hestur\nCATEGORY: Animals\nEMOJI: 🐴").emoji)
        assertNull(parseTranslationResponse("TRANSLATION: hestur\nCATEGORY: Animals").emoji)
        // A word instead of an emoji is dropped, but the translation still succeeds.
        val wordy = parseTranslationResponse("TRANSLATION: hestur\nCATEGORY: Animals\nEMOJI: horse")
        assertTrue(wordy.isSuccess)
        assertNull(wordy.emoji)
    }

    @Test
    fun categoryIsNotPollutedByEmojiLine() {
        assertEquals("Animals", parseTranslationResponse("TRANSLATION: hestur\nCATEGORY: Animals\nEMOJI: 🐴").finalCategory)
    }

    // --- Word splitting for dictionary lookups ---

    @Test
    fun punctuationIsSplitFromWords() {
        assertEquals(Triple("", "hestur", ","), splitPunctuation("hestur,"))
        assertEquals(Triple("¿", "Dónde", ""), splitPunctuation("¿Dónde"))
        assertEquals(Triple("\"", "it's", "\"!"), splitPunctuation("\"it's\"!"))
        assertEquals(Triple("—", "", ""), splitPunctuation("—"))
    }

    // --- "Last studied" labels ---

    @Test
    fun lastStudiedLabels() {
        val today = LocalDate.of(2026, 10, 9)
        fun at(date: LocalDate) = date.atTime(15, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

        assertEquals("Not studied yet", lastStudiedLabel(null, today.toKotlinLocalDate(), TimeZone.UTC))
        assertEquals("Studied today", lastStudiedLabel(at(today), today.toKotlinLocalDate(), TimeZone.UTC))
        assertEquals("Studied yesterday", lastStudiedLabel(at(today.minusDays(1)), today.toKotlinLocalDate(), TimeZone.UTC))
        assertEquals("Last studied 5 days ago", lastStudiedLabel(at(today.minusDays(5)), today.toKotlinLocalDate(), TimeZone.UTC))
    }

    // --- Reminder timing ---

    private val morning = LocalDateTime.of(2026, 10, 9, 8, 30)
    private val evening = LocalDateTime.of(2026, 10, 9, 19, 0)

    @Test
    fun fixedReminderFiresLaterTodayOrTomorrow() {
        assertEquals(Duration.ofMinutes(9 * 60 + 30), delayUntilNextReminder(LocalTime.of(18, 0), morning, allowToday = true))
        // Already past 18:00, so tomorrow.
        assertEquals(Duration.ofHours(23), delayUntilNextReminder(LocalTime.of(18, 0), evening, allowToday = true))
        // Right after a reminder fires, the next one is always tomorrow.
        assertEquals(Duration.ofHours(33).plusMinutes(30), delayUntilNextReminder(LocalTime.of(18, 0), morning, allowToday = false))
    }

    @Test
    fun randomReminderStaysInsideTheDayWindow() {
        repeat(200) { seed ->
            for ((now, allowToday) in listOf(morning to true, evening to true, morning to false)) {
                val fireAt = now.plus(delayUntilNextReminder(null, now, allowToday, Random(seed)))
                assertTrue("$fireAt", fireAt.isAfter(now))
                assertTrue("$fireAt", fireAt.hour in RANDOM_WINDOW_START_HOUR..RANDOM_WINDOW_END_HOUR)
                if (!allowToday) assertEquals(now.toLocalDate().plusDays(1), fireAt.toLocalDate())
            }
        }
    }
}
