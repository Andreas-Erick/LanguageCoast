package com.andreaserick.languagecoast.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andreaserick.languagecoast.data.BackupSummary
import com.andreaserick.languagecoast.ui.components.LocalUndoMessenger
import com.andreaserick.languagecoast.ui.components.plural
import com.andreaserick.languagecoast.ui.theme.CoralText
import com.andreaserick.languagecoast.ui.theme.DeepOceanBlue
import com.andreaserick.languagecoast.ui.theme.SandBeige
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Backups are refused above this size; one this large would hold far more than 50,000 cards with their progress. */
private const val MAX_BACKUP_CHARS = 30_000_000

/** MIME types offered when picking a backup. Some file managers label JSON files as plain text or bytes. */
private val BACKUP_MIME_TYPES = arrayOf("application/json", "text/*", "application/octet-stream")

/**
 * Back up everything in the app to one file and restore it, e.g. on a new phone. Files are saved and
 * opened with the system file picker, so they can go to Google Drive, Downloads and so on.
 */
@Composable
fun BackupSection(viewModel: BackupViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val undoMessenger = LocalUndoMessenger.current

    // Shows [text] briefly at the bottom of the screen.
    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val file = viewModel.buildBackup()
            val written = withContext(Dispatchers.IO) { writeText(context.contentResolver, uri, file.text) }
            toast(if (written) "Backed up ${plural(file.cardCount, "card")}" else "The backup couldn't be saved")
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) { readText(context.contentResolver, uri, MAX_BACKUP_CHARS) }
            if (text == null) viewModel.onRestoreFileUnreadable() else viewModel.onRestoreFileRead(text)
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            toast(it)
            viewModel.messageShown()
        }
    }

    Hint(
        "One file with all your coasts, cards and review progress, your streak and your settings. " +
            "Keep it somewhere safe to move to a new phone. API keys aren't included."
    )
    Spacer(modifier = Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = { backupLauncher.launch(viewModel.backupFileName()) },
            enabled = uiState.hasCoasts,
            modifier = Modifier
                .weight(1f)
                .height(50.dp)
        ) {
            Text("Back up", fontSize = 16.sp)
        }
        OutlinedButton(
            onClick = { restoreLauncher.launch(BACKUP_MIME_TYPES) },
            border = BorderStroke(1.dp, SandBeige),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = SandBeige),
            modifier = Modifier
                .weight(1f)
                .height(50.dp)
        ) {
            Text("Restore", fontSize = 16.sp)
        }
    }

    uiState.restoreSummary?.let { summary ->
        RestoreDialog(
            summary = summary,
            isRestoring = uiState.isRestoring,
            onRestore = {
                viewModel.confirmRestore { previous ->
                    undoMessenger.show("Backup restored") { viewModel.undoRestore(previous) }
                }
            },
            onDismiss = viewModel::cancelRestore
        )
    }
}

/** Says what the backup holds and that restoring it replaces everything in the app. */
@Composable
private fun RestoreDialog(summary: BackupSummary, isRestoring: Boolean, onRestore: () -> Unit, onDismiss: () -> Unit) {
    val date = Instant.ofEpochMilli(summary.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    AlertDialog(
        onDismissRequest = { if (!isRestoring) onDismiss() },
        containerColor = DeepOceanBlue,
        titleContentColor = SandBeige,
        textContentColor = Color.White,
        title = { Text("Restore backup?") },
        text = {
            Column {
                Text("Backup from $date")
                Text(
                    "${plural(summary.coastCount, "coast")} · ${plural(summary.islandCount, "island")} · " +
                        plural(summary.cardCount, "card"),
                    color = SandBeige
                )
                if (summary.streakCount > 0) Text("${summary.streakCount} day streak", color = SandBeige)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "This replaces all coasts, cards and settings on this phone. Your API keys stay as they are.",
                    color = CoralText
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onRestore, enabled = !isRestoring) {
                Text(if (isRestoring) "Restoring…" else "Restore", color = SandBeige)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isRestoring) { Text("Cancel", color = SandBeige) }
        }
    )
}
