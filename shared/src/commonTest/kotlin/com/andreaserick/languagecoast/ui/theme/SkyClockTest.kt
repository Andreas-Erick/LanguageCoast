package com.andreaserick.languagecoast.ui.theme

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class SkyClockTest {

    private val berlin = TimeZone.of("Europe/Berlin")

    private fun at(zone: TimeZone, y: Int, mo: Int, d: Int, h: Int, mi: Int): Instant =
        LocalDateTime(y, mo, d, h, mi).toInstant(zone)

    @Test
    fun everyLookIsReadableOnItsOwn() {
        SkyPhase.entries.forEach { assertTrue(isReadable(it.palette), "${it.label} has text below ${MIN_CONTRAST}:1") }
        assertTrue(isReadable(ClassicPalette), "Classic has text below ${MIN_CONTRAST}:1")
    }

    @Test
    fun everyMomentOfEveryBlendStaysReadable() {
        val day = listOf(SkyPhase.Night, SkyPhase.Sunrise, SkyPhase.Midday, SkyPhase.Evening, SkyPhase.Sunset, SkyPhase.Night)
        for (i in 0 until day.size - 1) {
            for (step in 0..200) {
                val t = step / 200f
                val p = blend(day[i].palette, day[i + 1].palette, t)
                assertTrue(isReadable(p), "${day[i].label} into ${day[i + 1].label} at $t is hard to read")
            }
        }
    }

    @Test
    fun aWholeYearOfMinutesInSeveralPlacesStaysReadable() {
        // Every 20 minutes of every 7th day, in places from the equator to the far north and south.
        val zones = listOf("Europe/Berlin", "Europe/Oslo", "America/Anchorage", "Asia/Singapore", "Australia/Hobart", "Etc/GMT-3")
        zones.map(TimeZone::of).forEach { zone ->
            for (week in 0 until 53) {
                val midnight = at(zone, 2026, 1, 1, 0, 0) + (week * 7 * 24 * 60).minutes
                for (step in 0 until 72) {
                    val now = midnight + (step * 20).minutes
                    val sky = SkyClock.skyAt(now, zone)
                    assertTrue(isReadable(sky.palette), "${zone.id} at $now (${sky.label}) is hard to read")
                }
            }
        }
    }

    @Test
    fun sunTimesMatchTheAlmanac() {
        // Berlin (52.5 N, 13.4 E): 21 June 2026 about 04:43 and 21:33 summer time, 21 December 08:15 and 15:54.
        fun local(minutes: Double, zone: TimeZone) =
            Instant.fromEpochMilliseconds((minutes * 60_000).toLong()).toLocalDateTime(zone).time.let { it.hour * 60 + it.minute }
        val june = SkyClock.sunTimes(LocalDate(2026, 6, 21), 52.5, 13.4)!!
        assertNear(4 * 60 + 43, local(june.first, berlin))
        assertNear(21 * 60 + 33, local(june.second, berlin))
        val december = SkyClock.sunTimes(LocalDate(2026, 12, 21), 52.5, 13.4)!!
        assertNear(8 * 60 + 15, local(december.first, berlin))
        assertNear(15 * 60 + 54, local(december.second, berlin))
        // Midsummer above the Arctic Circle: the sun doesn't set.
        assertEquals(null, SkyClock.sunTimes(LocalDate(2026, 6, 21), 69.6, 18.9))
    }

    private fun assertNear(expected: Int, actual: Int) =
        assertTrue(abs(expected - actual) <= 6, "expected about ${expected / 60}:${expected % 60}, got ${actual / 60}:${actual % 60}")

    @Test
    fun aNovemberDayInBerlinGoesThroughEveryLook() {
        // 21 November: sunrise about 07:43, sunset about 16:07.
        fun sky(h: Int, m: Int) = SkyClock.skyAt(at(berlin, 2026, 11, 21, h, m), berlin)
        assertEquals(Sky(SkyPhase.Night, SkyPhase.Night, 0f), sky(5, 0))
        assertEquals(SkyPhase.Sunrise, sky(8, 0).from)
        assertEquals(Sky(SkyPhase.Midday, SkyPhase.Midday, 0f), sky(11, 30))
        assertEquals(Sky(SkyPhase.Evening, SkyPhase.Evening, 0f), sky(14, 45))
        assertEquals(Sky(SkyPhase.Sunset, SkyPhase.Sunset, 0f), sky(16, 30))
        assertEquals(Sky(SkyPhase.Night, SkyPhase.Night, 0f), sky(19, 0))
        // Between two looks it is part of the way from one to the other.
        val dawn = sky(6, 58)
        assertEquals(SkyPhase.Night, dawn.from)
        assertEquals(SkyPhase.Sunrise, dawn.to)
        assertTrue(dawn.fraction in 0.1f..0.9f)
        assertEquals("Night into Sunrise", dawn.label)
    }

    @Test
    fun aJuneEveningInBerlinIsStillGoldenAtEight() {
        // Sunset about 21:33: at 20:00 it is evening, at 21:45 sunset, at 23:30 night.
        fun sky(h: Int, m: Int) = SkyClock.skyAt(at(berlin, 2026, 6, 21, h, m), berlin)
        assertEquals(SkyPhase.Evening, sky(20, 0).from)
        assertEquals(SkyPhase.Sunset, sky(21, 45).from)
        assertEquals(SkyPhase.Night, sky(23, 30).from)
    }

    @Test
    fun aZoneNotInTheTableGoesByItsOffset() {
        val zone = TimeZone.of("Etc/GMT-2") // two hours ahead of UTC
        val (lat, lon) = SkyClock.coordinatesOf(zone, at(zone, 2026, 3, 1, 12, 0))
        assertEquals(45.0, lat)
        assertEquals(30.0, lon)
        assertEquals(47.4 to 8.5, SkyClock.coordinatesOf(TimeZone.of("Europe/Zurich"), at(berlin, 2026, 3, 1, 12, 0)))
    }

    @Test
    fun theUsersLocationWinsOverTheTimeZone() {
        // Vigo, at the western edge of Spain's zone, sees the sun set about 20 minutes after Madrid does: at
        // 19:45 on 21 November it is night by the zone's city, but Vigo is still coming out of its sunset.
        val madrid = TimeZone.of("Europe/Madrid")
        val now = at(madrid, 2026, 11, 21, 19, 45)
        assertEquals(SkyPhase.Night, SkyClock.skyAt(now, madrid).from)
        assertEquals(SkyPhase.Sunset, SkyClock.skyAt(now, madrid, com.andreaserick.languagecoast.data.SunLocation(42.2, -8.7)).from)
    }
}
