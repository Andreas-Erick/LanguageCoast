package com.andreaserick.languagecoast.navigation

import kotlinx.serialization.Serializable

// Top-level destinations shown in the bottom navigation bar
@Serializable
object CreateScreenRoute

@Serializable
object MyCoastScreenRoute

@Serializable
object SettingsScreenRoute

// Search across all coasts, opened from My Coasts
@Serializable
object SearchScreenRoute

// The month's study recap, opened from My Coasts
@Serializable
object RecapScreenRoute

// The islands of a single coast, opened from My Coasts
@Serializable
data class CoastScreenRoute(
    val coastId: Int,
    val coastName: String
)

/**
 * A study session. [scope] says what it covers: one island ("island", the default), every island of a
 * coast ("coast"), today's due cards across all coasts ("today"), the leaky words the user keeps
 * forgetting ("leaky") or the daily session on one coast ("daily": a few due cards, then a few new
 * sentences). [islandName] doubles as the session's title for the other scopes.
 */
@Serializable
data class StudyScreenRoute(
    val islandId: Int = 0,
    val islandName: String = "",
    val scope: String = SCOPE_ISLAND,
    val coastId: Int = 0
) {
    companion object {
        const val SCOPE_ISLAND = "island"
        const val SCOPE_COAST = "coast"
        const val SCOPE_TODAY = "today"
        const val SCOPE_LEAKY = "leaky"
        const val SCOPE_DAILY = "daily"

        fun island(islandId: Int, islandName: String) = StudyScreenRoute(islandId = islandId, islandName = islandName)
        fun coast(coastId: Int, coastName: String) = StudyScreenRoute(islandName = coastName, scope = SCOPE_COAST, coastId = coastId)
        val Today = StudyScreenRoute(islandName = "Today's review", scope = SCOPE_TODAY)
        val Leaky = StudyScreenRoute(islandName = "Leaky words", scope = SCOPE_LEAKY)
        fun daily(coastId: Int, language: String) = StudyScreenRoute(islandName = "Today · $language", scope = SCOPE_DAILY, coastId = coastId)
    }
}

// The cards of a single island, opened from a coast or a study session
@Serializable
data class IslandScreenRoute(
    val islandId: Int,
    val islandName: String
)
