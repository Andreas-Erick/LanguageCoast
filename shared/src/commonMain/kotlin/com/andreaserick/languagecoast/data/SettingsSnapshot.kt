package com.andreaserick.languagecoast.data

import kotlinx.serialization.json.Json

/**
 * The settings without the API keys ([BackupSettings]) as a small file of their own. The settings file
 * itself holds the keys, so it is left out of the phone's own backup; the cards are not. Without this copy a
 * phone restored from its backup would bring the cards back with the streak, the native language and the
 * route's progress gone. Each platform writes the snapshot where its backup picks it up, and restores it at
 * start when the settings are empty ([SettingsRepository.isEmpty]).
 */
object SettingsSnapshot {
    /** The snapshot's file name, in the app's own files. */
    const val FILE_NAME = "settings-snapshot.json"

    private val format = Json {
        // A newer version of the app may have written fields this one doesn't know yet.
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** [settings] as the snapshot's text. */
    fun encode(settings: BackupSettings): String = format.encodeToString(BackupSettings.serializer(), settings)

    /**
     * The settings in a snapshot's text, or null if it isn't one, is cut off, or holds a date or time that
     * can't be read ([isReadable]): a damaged snapshot is passed over, never restored in part.
     */
    fun decode(json: String): BackupSettings? {
        val settings = try {
            format.decodeFromString(BackupSettings.serializer(), json)
        } catch (e: Exception) {
            // Not JSON, or not these settings. Whatever the reader makes of a damaged file, it is passed over.
            return null
        }
        return settings.takeIf { it.isReadable() }
    }
}
