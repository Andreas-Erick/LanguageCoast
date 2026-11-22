package com.andreaserick.languagecoast.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.andreaserick.languagecoast.data.SunLocation
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan
import kotlin.time.Instant

/** The looks the app goes through in a day when it follows the sun. */
enum class SkyPhase(val label: String, val palette: CoastPalette) {
    Night("Night", NightPalette),
    Sunrise("Sunrise", SunrisePalette),
    Midday("Midday", MiddayPalette),
    Evening("Evening", EveningPalette),
    Sunset("Sunset", SunsetPalette)
}

/** The sky at one moment: [from] turning into [to], [fraction] of the way there (0 while it is still [from]). */
data class Sky(val from: SkyPhase, val to: SkyPhase, val fraction: Float) {
    val palette: CoastPalette get() = blend(from.palette, to.palette, fraction)

    /** "Midday", or "Midday into Evening" while one turns into the other. */
    val label: String get() = if (from == to || fraction <= 0f) from.label else "${from.label} into ${to.label}"
}

/**
 * When each look of the day starts and ends, from the real sunrise and sunset where the phone is. Where that
 * is comes from the phone's approximate location if the user allows it, and otherwise from its time zone
 * (see [ZONE_COORDINATES]), which is right to within some minutes in most places but can be an hour off at
 * the far edge of a wide zone.
 *
 * A day goes: night, sunrise (from an hour before the sun is up until about an hour and a half after), midday,
 * evening (the last two or three hours of sun), sunset (from just before the sun goes until an hour after),
 * then night again. Each look holds for a while and blends into the next over half an hour to an hour.
 */
object SkyClock {

    /**
     * The sky at [now] for a phone in [zone], at [location] when the user lets the app use it, and otherwise
     * where [zone]'s city is.
     */
    fun skyAt(now: Instant, zone: TimeZone, location: SunLocation? = null): Sky {
        val date = now.toLocalDateTime(zone).date
        val (lat, lon) = location?.let { it.latitude to it.longitude } ?: coordinatesOf(zone, now)
        val sun = sunTimes(date, lat, lon)
        val (rise, set) = if (sun != null && sun.second - sun.first in MIN_DAY..MAX_DAY) {
            sun
        } else {
            // Polar day or night, or days too short or long to fit every look in: an ordinary day's times.
            val midnight = date.atStartOfDayIn(zone).epochMinutes()
            (midnight + DEFAULT_SUNRISE) to (midnight + DEFAULT_SUNSET)
        }
        return skyBetween(now.epochMinutes(), rise, set)
    }

    /** The sky at minute [now], on a day the sun rises at minute [rise] and sets at minute [set] (any epoch). */
    internal fun skyBetween(now: Double, rise: Double, set: Double): Sky {
        val stops = stops(rise, set)
        if (now < stops.first().first) return Sky(SkyPhase.Night, SkyPhase.Night, 0f)
        for (i in 0 until stops.size - 1) {
            val (t0, p0) = stops[i]
            val (t1, p1) = stops[i + 1]
            if (now < t1) {
                if (p0 == p1) return Sky(p0, p0, 0f)
                val linear = ((now - t0) / (t1 - t0)).toFloat().coerceIn(0f, 1f)
                // Eased, so a blend starts and ends gently instead of with a visible step.
                return Sky(p0, p1, linear * linear * (3f - 2f * linear))
            }
        }
        return Sky(SkyPhase.Night, SkyPhase.Night, 0f)
    }

    /**
     * The moments each look is fully there: a look holds between two stops with the same phase and blends
     * between two with different ones. On days shorter than ten hours the daytime stops close up in proportion.
     */
    private fun stops(rise: Double, set: Double): List<Pair<Double, SkyPhase>> {
        val start = rise - 15
        val length = set - rise
        // Where the daytime stops fall on a ten-hour day, as fractions of it from 15 minutes before sunrise.
        fun at(fraction: Double, minutesOnLongDay: Double): Double =
            if (length >= 600) minutesOnLongDay else start + fraction * length
        return listOf(
            rise - 75 to SkyPhase.Night,
            rise - 15 to SkyPhase.Sunrise,
            at(0.175, rise + 90) to SkyPhase.Sunrise,
            at(0.275, rise + 150) to SkyPhase.Midday,
            at(0.675, set - 210) to SkyPhase.Midday,
            at(0.775, set - 150) to SkyPhase.Evening,
            at(0.95, set - 45) to SkyPhase.Evening,
            set - 15 to SkyPhase.Sunset,
            set + 45 to SkyPhase.Sunset,
            set + 105 to SkyPhase.Night
        )
    }

    /**
     * Sunrise and sunset on [date] at [latitude], [longitude], in epoch minutes, after NOAA's general solar
     * position equations (good to a minute or two away from the poles). Null when the sun doesn't rise or set.
     */
    internal fun sunTimes(date: LocalDate, latitude: Double, longitude: Double): Pair<Double, Double>? {
        val gamma = 2 * PI / 365 * (date.dayOfYear - 1)
        val equationOfTime = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
            0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma))
        val declination = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) - 0.006758 * cos(2 * gamma) +
            0.000907 * sin(2 * gamma) - 0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
        val lat = latitude * PI / 180
        val cosHourAngle = cos(90.833 * PI / 180) / (cos(lat) * cos(declination)) - tan(lat) * tan(declination)
        if (cosHourAngle !in -1.0..1.0) return null
        val hourAngle = acos(cosHourAngle) * 180 / PI
        val midnightUtc = date.atStartOfDayIn(TimeZone.UTC).epochMinutes()
        val rise = midnightUtc + 720 - 4 * (longitude + hourAngle) - equationOfTime
        val set = midnightUtc + 720 - 4 * (longitude - hourAngle) - equationOfTime
        return rise to set
    }

    /**
     * Latitude and longitude of [zone]'s city. A zone that isn't in the table (a fixed offset such as
     * "GMT+2", or a new name) is put on the meridian its offset belongs to, at a mid-northern latitude.
     */
    internal fun coordinatesOf(zone: TimeZone, now: Instant): Pair<Double, Double> {
        zoneTable[zone.id]?.let { return it }
        val offsetMinutes = zone.offsetAt(now).totalSeconds / 60
        return FALLBACK_LATITUDE to (offsetMinutes / 4.0).coerceIn(-180.0, 180.0)
    }

    private val zoneTable: Map<String, Pair<Double, Double>> by lazy {
        ZONE_COORDINATES.split(';').mapNotNull { entry ->
            val parts = entry.split('|')
            if (parts.size != 3) return@mapNotNull null
            val lat = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            val lon = parts[2].toDoubleOrNull() ?: return@mapNotNull null
            parts[0] to (lat to lon)
        }.toMap()
    }

    private fun Instant.epochMinutes(): Double = toEpochMilliseconds() / 60_000.0

    private const val DEFAULT_SUNRISE = 6.5 * 60
    private const val DEFAULT_SUNSET = 19.5 * 60
    private const val MIN_DAY = 3.0 * 60
    private const val MAX_DAY = 20.0 * 60
    private const val FALLBACK_LATITUDE = 45.0
}

/**
 * [a] turned [t] of the way into [b]. Between two light looks or two dark ones every colour simply blends.
 * Between a light and a dark one the backgrounds shade over gradually, but text and accents switch from one
 * look's to the other's once, halfway; and the backgrounds stop short of where that text would get hard to
 * read, so every moment of the blend keeps the contrast each look has on its own.
 */
internal fun blend(a: CoastPalette, b: CoastPalette, t: Float): CoastPalette {
    if (t <= 0f) return a
    if (t >= 1f) return b
    if (a.isDark == b.isDark) {
        val mixed = mix(a, b, t, t)
        if (isReadable(mixed)) return mixed
        // A dip between two readable looks: keep the backgrounds nearer the look the text is nearer to.
        return mix(a, b, closestReadable(a, b, t, if (t < 0.5f) 0f else 1f, t), t)
    }
    val textT = if (t < 0.5f) 0f else 1f
    val mixed = mix(a, b, t, textT)
    if (isReadable(mixed)) return mixed
    return mix(a, b, closestReadable(a, b, t, textT, textT), textT)
}

/** The background position nearest [wanted], between it and [safe], at which text at [textT] stays readable. */
private fun closestReadable(a: CoastPalette, b: CoastPalette, wanted: Float, safe: Float, textT: Float): Float {
    var good = safe
    var bad = wanted
    repeat(14) {
        val middle = (good + bad) / 2
        if (isReadable(mix(a, b, middle, textT))) good = middle else bad = middle
    }
    return good
}

/** [a] and [b] mixed: backgrounds [backgroundT] of the way to [b], text and accents [textT] of the way. */
private fun mix(a: CoastPalette, b: CoastPalette, backgroundT: Float, textT: Float): CoastPalette {
    fun bg(x: CoastPalette.() -> Color) = lerp(a.x(), b.x(), backgroundT)
    fun fg(x: CoastPalette.() -> Color) = lerp(a.x(), b.x(), textT)
    return CoastPalette(
        oceanGround = bg { oceanGround },
        oceanSurface = bg { oceanSurface },
        cardEdge = bg { cardEdge },
        navGround = bg { navGround },
        surfaceCard = bg { surfaceCard },
        surfaceRaised = bg { surfaceRaised },
        surfaceSelected = bg { surfaceSelected },
        sunlit = fg { sunlit },
        sunlitText = fg { sunlitText },
        accentControl = fg { accentControl },
        onSunlit = fg { onSunlit },
        onColored = fg { onColored },
        textPrimary = fg { textPrimary },
        textSecondary = fg { textSecondary },
        coralText = fg { coralText },
        seafoam = fg { seafoam },
        onSeafoam = fg { onSeafoam },
        surfBlue = fg { surfBlue },
        waveLine = bg { waveLine },
        streakToday = bg { streakToday },
        chipContainer = bg { chipContainer },
        isDark = if (textT < 0.5f) a.isDark else b.isDark,
        stars = a.stars + (b.stars - a.stars) * backgroundT,
        glow = bg { glowColor },
        starColor = bg { starColor }
    )
}

/** The contrast every text colour needs on every surface, and text on the accent and success fills. */
internal const val MIN_CONTRAST = 4.5f

internal fun contrast(x: Color, y: Color): Float {
    val lx = x.luminance()
    val ly = y.luminance()
    return (maxOf(lx, ly) + 0.05f) / (minOf(lx, ly) + 0.05f)
}

/** Whether every text colour in [p] is readable on every surface of it. */
internal fun isReadable(p: CoastPalette): Boolean {
    val surfaces = listOf(p.oceanGround, p.oceanSurface, p.navGround, p.surfaceCard, p.surfaceRaised, p.surfaceSelected)
    val texts = listOf(p.textPrimary, p.textSecondary, p.sunlitText, p.accentControl, p.coralText, p.seafoam, p.surfBlue)
    return texts.all { text -> surfaces.all { contrast(text, it) >= MIN_CONTRAST } } &&
        contrast(p.onSunlit, p.sunlit) >= MIN_CONTRAST &&
        contrast(p.onSeafoam, p.seafoam) >= MIN_CONTRAST
}
