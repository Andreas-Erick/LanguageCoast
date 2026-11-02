package com.andreaserick.languagecoast.ui.create

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** Picks a photo with the system photo picker and reads its text with ML Kit, on the phone. */
@Composable
actual fun rememberPhotoTextReader(onText: (String) -> Unit, onError: (String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val currentOnText by rememberUpdatedState(onText)
    val currentOnError by rememberUpdatedState(onError)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrElse {
            currentOnError("That photo couldn't be opened.")
            return@rememberLauncherForActivityResult
        }
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
            .addOnSuccessListener { result ->
                val text = result.text.trim()
                if (text.isEmpty()) currentOnError("No text was found in that photo.") else currentOnText(text)
            }
            .addOnFailureListener { currentOnError("The photo's text couldn't be read: ${it.message ?: "try a sharper photo."}") }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}
