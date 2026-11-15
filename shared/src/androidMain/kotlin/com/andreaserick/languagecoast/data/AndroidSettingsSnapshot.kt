package com.andreaserick.languagecoast.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where the settings are stored, below the app's files folder: the file earlier versions created with `preferencesDataStoreFile("settings")`. */
const val SETTINGS_FILE_PATH = "datastore/settings.preferences_pb"

/**
 * Keeps a copy of the settings, without the API keys ([SettingsSnapshot]), in the app's files, where the
 * phone's own backup picks it up.
 *
 * The settings file itself is left out of that backup because it holds the keys (see backup_rules.xml and
 * data_extraction_rules.xml), while the cards are backed up. Without this copy, a restored or new phone would
 * get the cards back with the native language, the streak and the About you answers gone.
 */
class AndroidSettingsSnapshot(private val context: Context, private val settings: SettingsRepository) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val file = AtomicFile(File(context.filesDir, SettingsSnapshot.FILE_NAME))

    /** One save or restore at a time; also guards [lastSaved]. */
    private val mutex = Mutex()

    /** What the file holds, as far as this run knows, so that leaving the app without a change writes nothing. */
    private var lastSaved: String? = null

    /**
     * Whether the settings have to be brought back before anything reads them: there is a copy but no settings
     * file at all, which is how the app starts for the first time on a phone restored from a backup.
     */
    fun restoreIsDue(): Boolean = file.baseFile.exists() && !File(context.filesDir, SETTINGS_FILE_PATH).exists()

    /**
     * Brings the settings back from the copy when nothing at all is stored ([SettingsRepository.isEmpty]) and
     * a copy that can be read is there. The keys are not in it; they have to be entered again. Never throws:
     * without a usable copy the app simply starts with its defaults.
     */
    suspend fun restoreIfEmpty() {
        try {
            mutex.withLock {
                if (!settings.isEmpty()) return
                val text = try {
                    file.readFully().decodeToString()
                } catch (e: FileNotFoundException) {
                    return
                }
                val snapshot = SettingsSnapshot.decode(text)
                if (snapshot == null) {
                    logError(TAG, "The copy of the settings can't be read and is passed over")
                    return
                }
                settings.restoreSettings(snapshot)
                logInfo(TAG, "The settings were brought back from their copy")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError(TAG, "The settings couldn't be brought back from their copy", e)
        }
    }

    /**
     * Does [restoreIfEmpty] in the background, for a start where the settings file is there. It then only
     * finds something to do when that file was damaged and replaced by an empty one.
     */
    fun restoreIfEmptySoon() {
        scope.launch { restoreIfEmpty() }
    }

    /** Writes the copy in the background (see [save]); call it when the app is left. */
    fun saveSoon() {
        scope.launch { save() }
    }

    /**
     * Writes the copy, unless it already holds what is stored. Nothing is written while no settings are stored,
     * so an empty start never replaces a good copy. Never throws.
     */
    suspend fun save() {
        try {
            mutex.withLock {
                if (settings.isEmpty()) return
                val json = SettingsSnapshot.encode(settings.backupSettings())
                if (json == lastSaved) return
                // An AtomicFile is replaced whole or not at all, so a backup never picks up half a copy.
                val stream = file.startWrite()
                try {
                    stream.write(json.encodeToByteArray())
                    file.finishWrite(stream)
                } catch (e: IOException) {
                    file.failWrite(stream)
                    throw e
                }
                lastSaved = json
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError(TAG, "The copy of the settings couldn't be written", e)
        }
    }

    private companion object {
        const val TAG = "AndroidSettingsSnapshot"
    }
}
