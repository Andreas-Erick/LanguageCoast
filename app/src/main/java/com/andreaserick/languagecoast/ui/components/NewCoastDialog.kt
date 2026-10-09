package com.andreaserick.languagecoast.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.andreaserick.languagecoast.ui.theme.DeepOceanBlue
import com.andreaserick.languagecoast.ui.theme.SandBeige

/**
 * Lets the user pick a language to start a new coast for.
 *
 * @param availableLanguages Languages that don't have a coast yet.
 * @param onCreate Called with the chosen language.
 */
@Composable
fun NewCoastDialog(availableLanguages: List<String>, onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var language by remember(availableLanguages) { mutableStateOf(availableLanguages.firstOrNull()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Coast") },
        text = {
            Column {
                if (availableLanguages.isEmpty()) {
                    Text("You already have a coast for every supported language.")
                } else {
                    Text("Which language do you want to study?")
                    Spacer(modifier = Modifier.height(16.dp))
                    SelectionDropdown(
                        options = availableLanguages,
                        selected = language,
                        onSelected = { language = it },
                        optionLabel = { "$it Coast" }
                    )
                }
            }
        },
        containerColor = DeepOceanBlue,
        titleContentColor = SandBeige,
        textContentColor = Color.White,
        confirmButton = {
            TextButton(onClick = { language?.let(onCreate) }, enabled = language != null) {
                Text("Create", color = if (language != null) SandBeige else SandBeige.copy(alpha = 0.4f))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SandBeige)
            }
        }
    )
}
