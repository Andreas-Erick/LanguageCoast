package com.andreaserick.languagecoast.data

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import kotlin.coroutines.cancellation.CancellationException

/**
 * Keeps a copy of the settings, without the API keys ([SettingsSnapshot]), where an iPhone backup reaches it.
 *
 * The settings file itself is left out of backups because it holds the keys (see `settingsPath` in the iOS
 * platform module), while the cards are backed up. Without this copy, a restored or new iPhone would get the
 * cards back with the native language, the streak and the About you answers gone, and iPhone has no backup
 * file to fall back on. Android does the same with its AndroidSettingsSnapshot.
 */
internal object IosSettingsSnapshot {

    private const val TAG = "IosSettingsSnapshot"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** One save or restore at a time; also guards [lastSaved]. */
    private val mutex = Mutex()

    /** What the file holds, as far as this run knows, so that leaving the app without a change writes nothing. */
    private var lastSaved: String? = null

    /**
     * Brings the settings back from the copy when nothing at all is stored ([SettingsRepository.isEmpty]) and
     * a copy that can be read is there. The keys are not in it; they have to be entered again. Call it once at
     * launch, before any screen reads the settings. Never throws: without a usable copy the app simply starts
     * with its defaults.
     *
     * @param settingsFileIsThere Whether the settings file exists. Without one, which is how the app starts
     *     for the first time on an iPhone restored from a backup, this waits for the settings to be back, so
     *     the first screen already shows the right language. With one there is only something to do when the
     *     file was damaged and replaced by an empty one; that is looked into in the background, so that an
     *     ordinary start does not wait for it.
     */
    fun restoreIfSettingsAreGone(settings: SettingsRepository, settingsFileIsThere: Boolean) {
        val file = try {
            snapshotFile()?.takeIf { FileSystem.SYSTEM.exists(it) }
        } catch (e: Exception) {
            logError(TAG, "The copy of the settings could not be looked for", e)
            null
        } ?: return
        if (settingsFileIsThere) {
            scope.launch { restore(settings, file) }
        } else {
            runBlocking { restore(settings, file) }
        }
    }

    private suspend fun restore(settings: SettingsRepository, file: Path) {
        try {
            mutex.withLock {
                if (!settings.isEmpty()) return@withLock
                val snapshot = SettingsSnapshot.decode(FileSystem.SYSTEM.read(file) { readUtf8() })
                if (snapshot == null) {
                    logError(TAG, "The copy of the settings can't be read and is passed over")
                } else {
                    settings.restoreSettings(snapshot)
                    logInfo(TAG, "The settings were brought back from their copy")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError(TAG, "The settings could not be brought back from their copy", e)
        }
    }

    /**
     * Writes the copy in the background, unless it already holds what is stored; call it when the app is
     * left. Nothing is written while no settings are stored, so an empty start never replaces a good copy.
     */
    fun save(settings: SettingsRepository) {
        scope.launch {
            try {
                mutex.withLock {
                    if (settings.isEmpty()) return@withLock
                    val json = SettingsSnapshot.encode(settings.backupSettings())
                    if (json == lastSaved) return@withLock
                    val file = snapshotFile() ?: return@withLock
                    // Written beside the copy and moved over it, so a backup never picks up half a copy.
                    val draft = "$file.tmp".toPath()
                    FileSystem.SYSTEM.write(draft) { writeUtf8(json) }
                    FileSystem.SYSTEM.atomicMove(draft, file)
                    lastSaved = json
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError(TAG, "The copy of the settings could not be written", e)
            }
        }
    }

    /** The copy, directly in Application Support: iOS keeps that folder across updates and includes it in backups. */
    @OptIn(ExperimentalForeignApi::class)
    private fun snapshotFile(): Path? =
        NSFileManager.defaultManager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
            ?.path?.toPath()?.div(SettingsSnapshot.FILE_NAME)
}
