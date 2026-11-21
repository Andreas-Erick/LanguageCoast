package com.andreaserick.languagecoast.data

import kotlin.math.roundToInt

/**
 * Where the phone roughly is, for Follow the sun's sunrise and sunset. Kept to one decimal (about 10 km),
 * which moves the sun's times by well under a minute and says no more about the user than the town they are
 * in. It stays in the settings on the phone: it is never sent anywhere and left out of every backup.
 */
data class SunLocation(val latitude: Double, val longitude: Double) {

    /** As stored: "latitude,longitude". */
    fun encode(): String = "$latitude,$longitude"

    companion object {
        /** [latitude] and [longitude] rounded to one decimal, or null when they aren't a place on Earth. */
        fun rounded(latitude: Double, longitude: Double): SunLocation? {
            if (latitude.isNaN() || longitude.isNaN() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
            return SunLocation((latitude * 10).roundToInt() / 10.0, (longitude * 10).roundToInt() / 10.0)
        }

        /** What [encode] wrote, or null for anything else. */
        fun decode(text: String?): SunLocation? {
            val parts = text?.split(',') ?: return null
            if (parts.size != 2) return null
            return rounded(parts[0].toDoubleOrNull() ?: return null, parts[1].toDoubleOrNull() ?: return null)
        }
    }
}
