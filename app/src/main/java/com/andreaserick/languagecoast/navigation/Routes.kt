package com.andreaserick.languagecoast.navigation

import kotlinx.serialization.Serializable

// Top-level destinations shown in the bottom navigation bar
@Serializable
object CreateScreenRoute

@Serializable
object MyCoastScreenRoute

@Serializable
object SettingsScreenRoute

// Study session for a single island, opened from My Coast
@Serializable
data class StudyScreenRoute(
    val islandId: Int,
    val islandName: String
)