package com.andreaserick.languagecoast.ui.settings

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andreaserick.languagecoast.data.AnkiNote
import com.andreaserick.languagecoast.data.AnkiPackageContent
import com.andreaserick.languagecoast.data.Coast
import com.andreaserick.languagecoast.data.ExportFormat
import com.andreaserick.languagecoast.data.ankiNoteCards
import com.andreaserick.languagecoast.data.parseCardFile
import com.andreaserick.languagecoast.data.readAnkiPackage
import com.andreaserick.languagecoast.ui.components.ButtonHeight
import com.andreaserick.languagecoast.ui.components.CoastSecondaryButton
import com.andreaserick.languagecoast.ui.components.LocalUndoMessenger
import com.andreaserick.languagecoast.ui.components.SelectionDropdown
import com.andreaserick.languagecoast.ui.components.coastChipBorder
import com.andreaserick.languagecoast.ui.components.coastChipColors
import com.andreaserick.languagecoast.ui.components.plural
import com.andreaserick.languagecoast.ui.theme.SunlitText
import com.andreaserick.languagecoast.ui.theme.TextPrimary
import com.andreaserick.languagecoast.ui.theme.TextSecondary
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel

/** Import files larger than this are refused; a card file of this size would hold well over 50,000 cards. */
private const val MAX_IMPORT_CHARS = 5_000_000

/**
 * MIME types offered when picking a file to import. Some file managers label CSV files as Excel or as plain
 * bytes; an Anki package is a zip, and goes by that, by a name of its own or again by plain bytes.
 */
private val IMPORT_MIME_TYPES = arrayOf(
    "text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream",
    "application/zip", "application/apkg", "application/vnd.anki"
)

/**
 * Export cards to a file (Anki, CSV or Markdown) and import cards from an Anki package or a CSV / tab-separated file.
 * Files are saved and opened with the system file picker, so they can go to Downloads, Google Drive and so on.
 */
@Composable
actual fun CardTransferSection() {
    val viewModel: CardTransferViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val undoMessenger = LocalUndoMessenger.current

    var showExportDialog by rememberSaveable { mutableStateOf(false) }
    var isWorking by remember { mutableStateOf(false) }
    // Which of the two buttons started the work, so the progress label shows on that one.
    var importStarted by remember { mutableStateOf(false) }
    // The export chosen in the dialog, kept while the system "save as" screen is open.
    var exportCoastId by rememberSaveable { mutableStateOf<Int?>(null) }
    var exportFormat by rememberSaveable { mutableStateOf(ExportFormat.Anki) }

    // Shows [text] briefly at the bottom of the screen.
    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    // Writes the export chosen in the dialog to the file picked in the system "save as" screen, if any.
    fun writeExport(uri: Uri?) {
        uri ?: return
        if (isWorking) return
        isWorking = true
        importStarted = false
        val coastId = exportCoastId
        val format = exportFormat
        scope.launch {
            try {
                // Writing out a large coast takes a moment, so it is put together away from the main thread.
                val file = withContext(Dispatchers.Default) { viewModel.buildExport(coastId, format) }
                val written = withContext(Dispatchers.IO) { writeText(context.contentResolver, uri, file.text) }
                toast(if (written) "Exported ${plural(file.cardCount, "card")}" else "The file couldn't be saved.")
            } finally {
                isWorking = false
            }
        }
    }

    // One launcher per format, because the MIME type is fixed when a launcher is created.
    val exportLaunchers = ExportFormat.entries.associateWith { format ->
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(format.mimeType), ::writeExport)
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        if (isWorking) return@rememberLauncherForActivityResult
        isWorking = true
        importStarted = true
        scope.launch {
            try {
                // A deck can hold tens of thousands of notes, so its cards are read out away from the main thread too.
                when (val file = withContext(Dispatchers.IO) { readImportFile(context, uri) }) {
                    is ImportFile.Text ->
                        if (viewModel.acceptsImportText(file.text)) {
                            viewModel.onTextCardsRead(withContext(Dispatchers.Default) { parseCardFile(file.text) })
                        }
                    is ImportFile.AnkiPackage -> viewModel.onAnkiCardsRead(withContext(Dispatchers.Default) { ankiNoteCards(file.notes) })
                    is ImportFile.Unreadable -> viewModel.onImportFileUnreadable(file.reason)
                }
            } finally {
                isWorking = false
            }
        }
    }

    // Why a file couldn't be imported takes a sentence or two, which a toast cuts off after its second line.
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            undoMessenger.showMessage(it)
            viewModel.messageShown()
        }
    }

    Text("Export", style = MaterialTheme.typography.titleSmall)
    Hint("Save your cards as a file to import into Anki, as a CSV file for a spreadsheet, or as Markdown tables to read or print.")
    Spacer(modifier = Modifier.height(12.dp))
    CoastSecondaryButton(
        onClick = { showExportDialog = true },
        enabled = uiState.coasts.isNotEmpty() && !isWorking && !uiState.isImporting,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ButtonHeight)
    ) {
        Text(if (isWorking && !importStarted) "Exporting…" else "Export cards")
    }
    if (uiState.coasts.isEmpty()) Hint("Start a coast first: there are no cards to export yet.")

    Spacer(modifier = Modifier.height(20.dp))

    Text("Import", style = MaterialTheme.typography.titleSmall)
    // What it is for comes first, in words anyone knows; the file endings are for those who look for them.
    Hint("Add cards from another flashcard app (Anki) or a spreadsheet.")
    Text(
        "Anki decks (.apkg), and CSV or tab-separated files (.csv, .tsv).",
        color = TextSecondary,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(top = 2.dp)
    )
    Spacer(modifier = Modifier.height(12.dp))
    CoastSecondaryButton(
        onClick = { importLauncher.launch(IMPORT_MIME_TYPES) },
        enabled = !isWorking && !uiState.isImporting,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ButtonHeight)
    ) {
        Text(if (isWorking && importStarted) "Reading…" else "Import cards")
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
                    val skipped = when {
                        result.reformatted > 0 -> " (${result.duplicates} already there, ${result.reformatted} of them now with their line breaks)"
                        result.duplicates > 0 -> " (${result.duplicates} already there)"
                        else -> ""
                    }
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
@OptIn(ExperimentalLayoutApi::class)
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
        title = { Text("Export cards") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                FieldLabel("Cards from")
                SelectionDropdown(
                    options = coastOptions,
                    selected = coastOptions.firstOrNull { it.coastId == coastId } ?: coastOptions.first(),
                    onSelected = { onCoastChange(it.coastId) },
                    optionLabel = { it.label },
                    modifier = Modifier.semantics { contentDescription = "Cards from" }
                )
                Spacer(modifier = Modifier.height(16.dp))
                FieldLabel("Format")
                // The three formats don't fit side by side in a dialog on a phone, so they wrap.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportFormat.entries.forEach { option ->
                        FilterChip(
                            selected = option == format,
                            onClick = { onFormatChange(option) },
                            label = { Text(option.label, style = MaterialTheme.typography.labelMedium) },
                            colors = coastChipColors(),
                            border = coastChipBorder(enabled = true, selected = option == format),
                            shape = MaterialTheme.shapes.small
                        )
                    }
                }
                Hint(
                    when (format) {
                        ExportFormat.Anki -> "In Anki, use File › Import. Each island becomes a subdeck."
                        ExportFormat.Csv -> "For Excel, Google Sheets or Numbers: one row per card, with its island, alternatives and note."
                        ExportFormat.Markdown -> "One table per island, readable in any notes app."
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onExport, colors = ButtonDefaults.textButtonColors(contentColor = SunlitText)) {
                Text("Export", style = MaterialTheme.typography.labelMedium)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextPrimary, style = MaterialTheme.typography.labelMedium) } }
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
    // Worked out once per change of the options: with a large deck the list is long.
    val cards = remember(preview) { preview.cardsToImport }
    val coast = coasts.firstOrNull { it.coastId == preview.coastId }
    AlertDialog(
        onDismissRequest = { if (!isImporting) onDismiss() },
        title = { Text("Import ${plural(cards.size, "card")}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                FieldLabel("Add to")
                SelectionDropdown(
                    options = coasts,
                    selected = coast,
                    onSelected = { onCoastChange(it.coastId) },
                    optionLabel = { it.displayName },
                    modifier = Modifier.semantics { contentDescription = "Add to" }
                )
                Spacer(modifier = Modifier.height(16.dp))
                FieldLabel("First cards")
                cards.take(3).forEach { card ->
                    Text(
                        // One line per card here, however many its sides have.
                        text = "${card.nativeText} → ${card.targetText}".replace("\n", " · "),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = card.island ?: DEFAULT_IMPORT_ISLAND,
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Hint("The left side is shown first when you study; it should be in your native language.")
                Spacer(modifier = Modifier.height(8.dp))
                SwitchRow("Swap sides", preview.swapColumns, onSwapChange)
                if (preview.mayHaveHeader) SwitchRow("First row is a header", preview.skipFirstRow, onSkipFirstRowChange)
            }
        },
        confirmButton = {
            TextButton(onClick = onImport, enabled = cards.isNotEmpty() && !isImporting) {
                Text(if (isImporting) "Importing…" else "Import", color = SunlitText, style = MaterialTheme.typography.labelMedium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isImporting) { Text("Cancel", color = TextPrimary, style = MaterialTheme.typography.labelMedium) }
        }
    )
}

/**
 * A label with a switch at the end of the row. The whole row is the switch: a tap anywhere on it flips it,
 * and a screen reader says the label with the state.
 */
@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** What a file picked for import holds. */
private sealed interface ImportFile {
    /** Text, to look for rows of cards in. */
    data class Text(val text: String) : ImportFile

    /** The notes of an Anki package. */
    data class AnkiPackage(val notes: List<AnkiNote>) : ImportFile

    /** Nothing that can be imported; [reason] says why when there is more to say than that. */
    data class Unreadable(val reason: String? = null) : ImportFile
}

/** The first bytes of every zip file, which an Anki package is. */
private val ZIP_SIGNATURE = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4)

/**
 * Reads the file picked for import. What it is goes by its first bytes, not by its name, which a file
 * manager may not pass on: a zip is unpacked as an Anki package, anything else is read as text.
 */
private fun readImportFile(context: Context, uri: Uri): ImportFile = try {
    context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
        input.mark(ZIP_SIGNATURE.size)
        val start = ByteArray(ZIP_SIGNATURE.size)
        var read = 0
        while (read < start.size) {
            val count = input.read(start, read, start.size - read)
            if (count == -1) break
            read += count
        }
        input.reset()
        if (read == start.size && start.contentEquals(ZIP_SIGNATURE)) {
            val workDir = File(context.cacheDir, "anki-import-${java.util.UUID.randomUUID()}")
            when (val content = readAnkiPackage(input, workDir)) {
                is AnkiPackageContent.Notes -> ImportFile.AnkiPackage(content.notes)
                AnkiPackageContent.NotAPackage ->
                    ImportFile.Unreadable("That zip file isn't an Anki package. From a spreadsheet, save the cards as CSV and import that.")
                AnkiPackageContent.Unreadable ->
                    ImportFile.Unreadable("That Anki package couldn't be read. Export it again; if that doesn't help, tick \"Support older Anki versions\" when exporting.")
            }
        } else {
            readText(input, MAX_IMPORT_CHARS)?.let { ImportFile.Text(it) } ?: ImportFile.Unreadable()
        }
    } ?: ImportFile.Unreadable()
} catch (e: IOException) {
    ImportFile.Unreadable()
} catch (e: SecurityException) {
    ImportFile.Unreadable()
} catch (e: OutOfMemoryError) {
    ImportFile.Unreadable("That file is too large to read on this phone.")
}

/** Reads a text file, or returns null if it can't be read or is longer than [maxChars]. */
internal fun readText(resolver: ContentResolver, uri: Uri, maxChars: Int = MAX_IMPORT_CHARS): String? = try {
    resolver.openInputStream(uri)?.use { readText(it, maxChars) }
} catch (e: IOException) {
    null
} catch (e: SecurityException) {
    null
} catch (e: OutOfMemoryError) {
    null
}

/** The text [input] holds, or null if it is longer than [maxChars]. */
private fun readText(input: InputStream, maxChars: Int): String? {
    val reader = input.bufferedReader()
    val text = StringBuilder()
    val chunk = CharArray(8 * 1024)
    while (text.length <= maxChars) {
        val read = reader.read(chunk)
        if (read == -1) break
        text.appendRange(chunk, 0, read)
    }
    return text.takeIf { it.length <= maxChars }?.toString()
}

/** Writes [text] to [uri], replacing what was there. Returns whether it worked. */
internal fun writeText(resolver: ContentResolver, uri: Uri, text: String): Boolean = try {
    resolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) } != null
} catch (e: IOException) {
    false
} catch (e: SecurityException) {
    false
}
