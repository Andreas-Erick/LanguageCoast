package com.andreaserick.languagecoast.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreaserick.languagecoast.data.Backup
import com.andreaserick.languagecoast.data.BackupException
import com.andreaserick.languagecoast.data.BackupManager
import com.andreaserick.languagecoast.data.BackupSummary
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.decodeBackup
import com.andreaserick.languagecoast.data.encodeBackup
import com.andreaserick.languagecoast.data.summary
import com.andreaserick.languagecoast.notifications.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class BackupUiState(
    /** Whether there is anything to back up. */
    val hasCoasts: Boolean = false,
    /** A backup read from a file, waiting for the user to confirm restoring it. */
    val restorePreview: Backup? = null,
    val isRestoring: Boolean = false,
    /** A one-off message for the user (e.g. why a file can't be restored), cleared by [BackupViewModel.messageShown]. */
    val message: String? = null
) {
    val restoreSummary: BackupSummary? get() = restorePreview?.summary()
}

/** A backup ready to be written to a file. */
data class BackupFile(val text: String, val cardCount: Int)

/**
 * Backs up everything in the app to one file and restores it, e.g. on a new phone. Restoring replaces
 * everything, so what was there before is kept until the user can no longer undo.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backups: BackupManager,
    flashcards: FlashcardRepository,
    private val reminderScheduler: ReminderScheduler,
    private val clock: Clock
) : ViewModel() {

    private val state = MutableStateFlow(BackupUiState())

    val uiState: StateFlow<BackupUiState> =
        combine(state, flashcards.observeCoasts()) { s, coasts -> s.copy(hasCoasts = coasts.isNotEmpty()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    /** A backup of everything in the app, as the file's text. */
    suspend fun buildBackup(): BackupFile {
        val backup = backups.createBackup()
        return BackupFile(encodeBackup(backup), backup.cards.size)
    }

    /** A file name with today's date, like "language-coast-backup-2026-10-11.json". */
    fun backupFileName(): String = "language-coast-backup-${LocalDate.now(clock)}.json"

    /** Reads the backup in [text] and asks to confirm restoring it, or explains why it can't be restored. */
    fun onRestoreFileRead(text: String) {
        try {
            val backup = decodeBackup(text)
            state.update { it.copy(restorePreview = backup) }
        } catch (e: BackupException) {
            showMessage(e.message ?: "That file can't be restored.")
        }
    }

    /** Tells the user the picked file couldn't be read. */
    fun onRestoreFileUnreadable() = showMessage("That file couldn't be read.")

    /** Closes the restore confirmation without changing anything. */
    fun cancelRestore() = state.update { it.copy(restorePreview = null) }

    /**
     * Replaces everything in the app with the previewed backup. [onRestored] receives a backup of what
     * was there before, so the restore can be undone with [undoRestore].
     */
    fun confirmRestore(onRestored: (previous: Backup) -> Unit) {
        val backup = state.value.restorePreview ?: return
        state.update { it.copy(isRestoring = true) }
        viewModelScope.launch {
            val previous = backups.createBackup()
            backups.restore(backup)
            // The backup may turn reminders on or off, or move them to another time.
            reminderScheduler.reschedule()
            state.update { it.copy(restorePreview = null, isRestoring = false) }
            onRestored(previous)
        }
    }

    /** Puts back what was there before a restore. Not tied to this ViewModel's scope, so undo works after leaving the screen. */
    suspend fun undoRestore(previous: Backup) {
        backups.restore(previous)
        reminderScheduler.reschedule()
    }

    /** Clears the message once it has been shown. */
    fun messageShown() = state.update { it.copy(message = null) }

    /** Shows [message] to the user once. */
    private fun showMessage(message: String) = state.update { it.copy(message = message) }
}
