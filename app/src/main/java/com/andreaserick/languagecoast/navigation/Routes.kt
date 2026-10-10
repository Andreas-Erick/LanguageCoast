package com.andreaserick.languagecoast.navigation

import kotlinx.serialization.Serializable

// Top-level destinations shown in the bottom navigation bar
@Serializable
object CreateScreenRoute

@Serializable
object MyCoastScreenRoute

@Serializable
object SettingsScreenRoute

// The islands of a single coast, opened from My Coasts
@Serializable
data class CoastScreenRoute(
    val coastId: Int,
    val coastName: String
)

// Study session for a single island, opened from a coast
@Serializable
data class StudyScreenRoute(
    val islandId: Int,
    val islandName: String
)
// The cards of a single island, opened from a coast or a study session
@Serializable
data class IslandScreenRoute(
    val islandId: Int,
    val islandName: String
)
