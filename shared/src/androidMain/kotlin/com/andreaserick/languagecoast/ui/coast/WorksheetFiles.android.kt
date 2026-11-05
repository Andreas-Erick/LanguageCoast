package com.andreaserick.languagecoast.ui.coast

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.andreaserick.languagecoast.data.WorksheetPdf
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PDF = "application/pdf"

/**
 * Downloading puts the PDF straight into the phone's Downloads folder, where the Files app and every PDF
 * viewer find it. Android 9 and older only let an app write there with a storage permission, so on those
 * the system's "save as" screen asks where it should go instead. Opening hands the copy in the cache to a
 * PDF viewer through the app's FileProvider.
 */
@Composable
actual fun rememberWorksheetFiles(): WorksheetFiles? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The file being saved while the "save as" screen is open, and who to tell how it went.
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingResult by remember { mutableStateOf<((WorksheetDownload) -> Unit)?>(null) }

    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PDF)) { uri ->
        val path = pendingPath
        val onResult = pendingResult
        pendingPath = null
        pendingResult = null
        when {
            uri == null -> onResult?.invoke(WorksheetDownload.Cancelled)
            path != null -> scope.launch {
                val written = withContext(Dispatchers.IO) { copy(context.contentResolver, File(path), uri) }
                onResult?.invoke(if (written) WorksheetDownload.Saved(place = null) else WorksheetDownload.Failed)
            }
        }
    }

    return remember(context) {
        WorksheetFiles(
            download = { pdf, onResult ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) { saveToDownloads(context, pdf) }
                        onResult(if (saved) WorksheetDownload.Saved(place = "Downloads") else WorksheetDownload.Failed)
                    }
                } else {
                    pendingPath = pdf.path
                    pendingResult = onResult
                    saveAs.launch(pdf.fileName)
                }
            },
            open = { pdf -> openPdf(context, pdf) }
        )
    }
}

/** Adds [pdf] to the Downloads collection; a file of the same name there gets a number added, not replaced. */
@RequiresApi(Build.VERSION_CODES.Q)
private fun saveToDownloads(context: Context, pdf: WorksheetPdf): Boolean {
    val resolver = context.contentResolver
    val entry = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, pdf.fileName)
        put(MediaStore.Downloads.MIME_TYPE, PDF)
        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        // Hidden from other apps until it is written in full.
        put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val uri = try {
        resolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), entry)
    } catch (e: RuntimeException) {
        null
    } ?: return false
    if (!copy(resolver, File(pdf.path), uri)) {
        runCatching { resolver.delete(uri, null, null) }
        return false
    }
    return runCatching { resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null) }.isSuccess
}

/** Writes [file] to [uri]. Returns whether it worked. */
private fun copy(resolver: ContentResolver, file: File, uri: Uri): Boolean = try {
    resolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } } != null
} catch (e: IOException) {
    false
} catch (e: SecurityException) {
    false
}

/** Shows [pdf] in the phone's PDF viewer. Returns false if there is none, or the file is gone from the cache. */
private fun openPdf(context: Context, pdf: WorksheetPdf): Boolean {
    val file = File(pdf.path)
    if (!file.exists()) return false
    return try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, PDF)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: IllegalArgumentException) {
        false
    }
}
