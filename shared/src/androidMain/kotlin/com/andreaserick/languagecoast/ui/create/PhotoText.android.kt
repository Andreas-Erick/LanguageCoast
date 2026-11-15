package com.andreaserick.languagecoast.ui.create

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.andreaserick.languagecoast.data.logError
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "PhotoText"

/**
 * A photo is decoded at half its size, and half again, for as long as its longer side stays at least this
 * many pixels and its shorter side at least [MIN_SHORT_SIDE]. That is plenty for the text of a page or a
 * sign, and a fraction of the memory a camera's full picture takes.
 */
private const val MIN_LONG_SIDE = 2000

/** A long screenshot is narrow: halved by its length alone, its text would be too small to read. */
private const val MIN_SHORT_SIDE = 1000

/**
 * Picks a photo with the system photo picker and reads its text with ML Kit, on the phone. The photo is
 * opened off the main thread, since decoding a camera picture takes a moment.
 */
@Composable
actual fun rememberPhotoTextReader(onText: (String) -> Unit, onError: (String) -> Unit, onReading: (Boolean) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnText by rememberUpdatedState(onText)
    val currentOnError by rememberUpdatedState(onError)
    val currentOnReading by rememberUpdatedState(onReading)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        currentOnReading(true)
        scope.launch {
            val image = withContext(Dispatchers.IO) { loadImage(context, uri) }
            if (image == null) {
                currentOnReading(false)
                currentOnError("That photo couldn't be opened.")
                return@launch
            }
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    val text = result.text.trim()
                    if (text.isEmpty()) currentOnError("No text was found in that photo.") else currentOnText(text)
                }
                .addOnFailureListener { error ->
                    logError(TAG, "The photo's text couldn't be read", error)
                    currentOnError("The photo's text couldn't be read. Try a sharper photo.")
                }
                .addOnCompleteListener {
                    recognizer.close()
                    currentOnReading(false)
                }
        }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

/**
 * The photo at [uri] ready for text recognition: reduced in size (see [MIN_LONG_SIDE]) and with the turn
 * its EXIF data asks for. Null if it can't be opened. Reads the file, so not for the main thread.
 */
private fun loadImage(context: Context, uri: Uri): InputImage? = try {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (
        maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MIN_LONG_SIDE &&
        minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MIN_SHORT_SIDE
    ) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    // No bitmap means a format this decoder doesn't know: ML Kit's own loader may still manage it, at full size.
    if (bitmap != null) InputImage.fromBitmap(bitmap, rotationOf(context, uri)) else InputImage.fromFilePath(context, uri)
} catch (e: Throwable) {
    // Throwable, because a picture too large for memory ends in an OutOfMemoryError, which is no Exception.
    logError(TAG, "The photo couldn't be opened", e)
    null
}

/** How far the photo at [uri] must be turned clockwise to stand upright, from its EXIF data; 0 if that says nothing. */
private fun rotationOf(context: Context, uri: Uri): Int = try {
    val orientation = context.contentResolver.openInputStream(uri)?.use {
        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
} catch (e: Exception) {
    0
}
