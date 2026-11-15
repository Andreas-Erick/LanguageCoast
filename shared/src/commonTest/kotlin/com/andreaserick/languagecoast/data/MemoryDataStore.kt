package com.andreaserick.languagecoast.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Settings kept in memory, for tests that write more than once: a store in a file can't replace the file it
 * still has open on Windows (see the note in SettingsRepositoryTest), so those tests are limited to one write.
 */
internal class MemoryDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

/** A [SettingsRepository] on a [MemoryDataStore], with the clock at noon UTC on 2026-10-09. */
internal fun memorySettings(store: DataStore<Preferences> = MemoryDataStore()): SettingsRepository =
    DataStoreSettingsRepository(store, MEMORY_SETTINGS_CLOCK, kotlinx.datetime.TimeZone.UTC)

internal val MEMORY_SETTINGS_CLOCK: kotlin.time.Clock = object : kotlin.time.Clock {
    override fun now() = kotlin.time.Instant.parse("2026-10-09T12:00:00Z")
}
