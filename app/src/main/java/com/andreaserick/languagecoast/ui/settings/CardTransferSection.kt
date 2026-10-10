package com.andreaserick.languagecoast.ui.settings

import android.content.ContentResolver
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andreaserick.languagecoast.data.Coast
import com.andreaserick.languagecoast.data.ExportFormat
import com.andreaserick.languagecoast.ui.components.LocalUndoMessenger
import com.andreaserick.languagecoast.ui.components.SelectionDropdown
import com.andreaserick.languagecoast.ui.components.plural
import com.andreaserick.languagecoast.ui.theme.DeepOceanBlue
import com.andreaserick.languagecoast.ui.theme.MistWhite
import com.andreaserick.languagecoast.ui.theme.SandBeige
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** Import files larger than this are refused; a card file of this size would hold well over 50,000 cards. */
private const val MAX_IMPORT_CHARS = 5_000_000

/** MIME types offered when picking a file to import. Some file managers label CSV files as Excel or as plain bytes. */
private val IMPORT_MIME_TYPES = arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")

/**
 * Export cards to a file (Anki or Markdown) and import cards from a CSV / tab-separated file.
 * Files are saved and opened with the system file picker, so they can go to Downloads, Google Drive and so on.
 */
@Composable
fun CardTransferSection(viewModel: CardTransferViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val undoMessenger = LocalUndoMessenger.current

    var showExportDialog by rememberSaveable { mutableStateOf(false) }
    // The export chosen in the dialog, kept while the system "save as" screen is open.
    var exportCoastId by rememberSaveable { mutableStateOf<Int?>(null) }
    var exportFormat by rememberSaveable { mutableStateOf(ExportFormat.Anki) }

    // Shows [text] briefly at the bottom of the screen.
    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    // Writes the export chosen in the dialog to the file picked in the system "save as" screen, if any.
    fun writeExport(uri: Uri?) {
        uri ?: return
        scope.launch {
            val file = viewModel.buildExport(exportCoastId, exportFormat)
            val written = withContext(Dispatchers.IO) { writeText(context.contentResolver, uri, file.text) }
            toast(if (written) "Exported ${plural(file.cardCount, "card")}" else "The file couldn't be saved")
        }
    }

    // One launcher per format, because the MIME type is fixed when a launcher is created.
    val exportLaunchers = ExportFormat.entries.associateWith { format ->
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(format.mimeType), ::writeExport)
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) { readText(context.contentResolver, uri) }
            if (text == null) viewModel.onImportFileUnreadable() else viewModel.onImportFileRead(text)
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            toast(it)
            viewModel.messageShown()
        }
    }

    Text("Export", color = Color.White)
    Hint("Save your cards as a file to import into Anki, or as Markdown tables to read or print.")
    Spacer(modifier = Modifier.height(12.dp))
    Button(
        onClick = { showExportDialog = true },
        enabled = uiState.coasts.isNotEmpty(),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        Text("Export cards", fontSize = 16.sp)
    }

    Spacer(modifier = Modifier.height(20.dp))

    Text("Import", color = Color.White)
    Hint("Add cards from a CSV or tab-separated file, e.g. one exported from Anki or a spreadsheet.")
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedButton(
        onClick = { importLauncher.launch(IMPORT_MIME_TYPES) },
        border = BorderStroke(1.dp, SandBeige),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = SandBeige),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        Text("Import cards", fontSize = 16.sp)
    }

    if (showExportDialog) {
        ExportDialog(
            coasts = uiState.coasts,
            coastId = exportCoastId,
            format = exportFormat,
            onCoastChange = { exportCoastId = it },
            onFormatChange = { exportFormat = it },
            onExport = {
                showExportDialog = false
                exportLaunchers.getValue(exportFormat).launch(viewModel.exportFileName(exportCoastId, exportFormat))
            },
            onDismiss = { showExportDialog = false }
        )
    }

    uiState.importPreview?.let { preview ->
        ImportDialog(
            preview = preview,
            coasts = uiState.coasts,
            isImporting = uiState.isImporting,
            onCoastChange = viewModel::setImportCoast,
            onSwapChange = viewModel::setSwapColumns,
            onSkipFirstRowChange = viewModel::setSkipFirstRow,
            onImport = {
                viewModel.confirmImport { result ->
                    val skipped = if (result.duplicates > 0) " (${result.duplicates} already there)" else ""
                    if (result.added.isEmpty()) {
                        toast("Nothing new to import$skipped")
                    } else {
                        undoMessenger.show("Imported ${plural(result.added.size, "card")}$skipped") { viewModel.undoImport(result) }
                    }
                }
            },
            onDismiss = viewModel::cancelImport
        )
    }
}

/** Asks which coast (or all of them) to export and in which format. */
@Composable
private fun ExportDialog(
    coasts: List<Coast>,
    coastId: Int?,
    format: ExportFormat,
    onCoastChange: (Int?) -> Unit,
    onFormatChange: (ExportFormat) -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit
) {
    // A null coast ID stands for "All coasts".
    val coastOptions = listOf(CoastOption(null, "All coasts")) + coasts.map { CoastOption(it.coastId, it.displayName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeepOceanBlue,
        titleContentColor = SandBeige,
        textContentColor = Color.White,
        title = { Text("Export cards") },
        text = {
            Column {
                FieldLabel("Cards from")
                SelectionDropdown(
                    options = coastOptions,
                    selected = coastOptions.firstOrNull { it.coastId == coastId } ?: coastOptions.first(),
                    onSelected = { onCoastChange(it.coastId) },
                    optionLabel = { it.label }
                )
                Spacer(modifier = Modifier.height(16.dp))
                FieldLabel("Format")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportFormat.entries.forEach { option ->
                        FilterChip(
                            selected = option == format,
                            onClick = { onFormatChange(option) },
                            label = { Text(option.label) },
                            colors = coastChipColors()
                        )
                    }
                }
                Hint(
                    when (format) {
                        ExportFormat.Anki -> "In Anki, use File › Import. Each island becomes a subdeck."
                        ExportFormat.Markdown -> "One table per island, readable in any notes app."
                    }
                )
            }
        },
        confirmButton = { TextButton(onClick = onExport) { Text("Export", color = SandBeige) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = SandBeige) } }
    )
}

private data class CoastOption(val coastId: Int?, val label: String)

/**
 * Shows the first cards of the file being imported and lets the user pick the coast, swap the sides
 * or leave out a header row before importing.
 */
@Composable
private fun ImportDialog(
    preview: ImportPreview,
    coasts: List<Coast>,
    isImporting: Boolean,
    onCoastChange: (Int) -> Unit,
    onSwapChange: (Boolean) -> Unit,
    onSkipFirstRowChange: (Boolean) -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit
) {
    val cards = preview.cardsToImport
    val coast = coasts.firstOrNull { it.coastId == preview.coastId }
    AlertDialog(
        onDismissRequest = { if (!isImporting) onDismiss() },
        containerColor = DeepOceanBlue,
        titleContentColor = SandBeige,
        textContentColor = Color.White,
        title = { Text("Import ${plural(cards.size, "card")}") },
        text = {
            Column {
                FieldLabel("Add to")
                SelectionDropdown(
                    options = coasts,
                    selected = coast,
                    onSelected = { onCoastChange(it.coastId) },
                    optionLabel = { it.displayName }
                )
                Spacer(modifier = Modifier.height(16.dp))
                FieldLabel("First cards")
                cards.take(3).forEach { card ->
                    Text(
                        text = "${card.nativeText} → ${card.targetText}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = card.island ?: DEFAULT_IMPORT_ISLAND,
                        color = MistWhite,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Hint("The left side is shown first when you study; it should be in your native language.")
                Spacer(modifier = Modifier.height(8.dp))
                SwitchRow("Swap sides", preview.swapColumns, onSwapChange)
                SwitchRow("First row is a header", preview.skipFirstRow, onSkipFirstRowChange)
            }
        },
        confirmButton = {
            TextButton(onClick = onImport, enabled = cards.isNotEmpty() && !isImporting) {
                Text(if (isImporting) "Importing…" else "Import", color = SandBeige)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isImporting) { Text("Cancel", color = SandBeige) }
        }
    )
}

/** A label with a switch at the end of the row. */
@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = coastSwitchColors())
    }
}

/** Reads a text file, or returns null if it can't be read or is longer than [maxChars]. */
internal fun readText(resolver: ContentResolver, uri: Uri, maxChars: Int = MAX_IMPORT_CHARS): String? = try {
    resolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
        val text = StringBuilder()
        val chunk = CharArray(8 * 1024)
        while (text.length <= maxChars) {
            val read = reader.read(chunk)
            if (read == -1) break
            text.appendRange(chunk, 0, read)
        }
        text.takeIf { it.length <= maxChars }?.toString()
    }
} catch (e: IOException) {
    null
} catch (e: SecurityException) {
    null
}

/** Writes [text] to [uri], replacing what was there. Returns whether it worked. */
internal fun writeText(resolver: ContentResolver, uri: Uri, text: String): Boolean = try {
    resolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) } != null
} catch (e: IOException) {
    false
} catch (e: SecurityException) {
    false
}
