package com.andreaserick.languagecoast.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.andreaserick.languagecoast.data.CloudProvider
import com.andreaserick.languagecoast.data.TranslationProvider
import com.andreaserick.languagecoast.ui.components.ButtonHeight
import com.andreaserick.languagecoast.ui.components.CoastButton
import com.andreaserick.languagecoast.ui.components.CoastSecondaryButton
import com.andreaserick.languagecoast.ui.components.CoastTextField
import com.andreaserick.languagecoast.ui.theme.OnSunlit
import com.andreaserick.languagecoast.ui.theme.Sunlit
import com.andreaserick.languagecoast.ui.theme.TextPrimary
import com.andreaserick.languagecoast.ui.theme.TextSecondary

/**
 * The guided way to an API key for [cloud], for someone who has never made one: open the provider's page,
 * make the key there, paste it here. Each step is one thing to do, with the button for it under the step.
 *
 * Saving goes through [rememberCloudKeySave], the same way the key field in Settings saves, so the
 * permission to send text to the provider is asked here exactly as it is there. The sheet closes once the
 * key is stored; the Translation card it opened from then shows whether the provider accepted it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KeySetupSheet(cloud: CloudProvider, uiState: SettingsUiState, viewModel: SettingsViewModel, onDismiss: () -> Unit) {
    val provider = cloud.provider
    val openLink = rememberOpenLink()
    // getText is the one clipboard read that is the same on Android and iOS; its successor hands out platform types.
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    // A secret that isn't saved yet: not kept across a restart of the app.
    var input by remember { mutableStateOf("") }
    var pasteNote by remember { mutableStateOf<String?>(null) }
    var keyVisible by remember { mutableStateOf(false) }
    val save = rememberCloudKeySave(provider, uiState, viewModel, onSaved = onDismiss)
    val canSave = input.any { !it.isWhitespace() }
    // What the provider's keys start with, from the field's placeholder ("AIzaSy..."), so the user knows what to copy.
    val keyStart = cloud.keyCopy.placeholder.removeSuffix("...").takeIf { it.isNotBlank() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            Column {
                Text("Set up ${provider.label}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (provider == RECOMMENDED_PROVIDER) "It's free to start and takes about 2 minutes." else "It takes about 2 minutes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            SetupStep(
                number = 1,
                title = "Open ${cloud.keyCopy.site}",
                detail = "Sign in with your ${cloud.company} account, or make one there."
            ) {
                CoastSecondaryButton(
                    onClick = { openLink(cloud.keyUrl) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ButtonHeight)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open ${cloud.keyCopy.site}")
                }
            }

            SetupStep(
                number = 2,
                title = "Tap Create API key and copy it",
                detail = "The key is a long code" + (keyStart?.let { " that usually starts with \"$it\"" } ?: "") +
                    ". Copy all of it, then come back here."
            )

            SetupStep(
                number = 3,
                title = "Paste it here",
                detail = "It is stored only on this phone and sent only to ${cloud.company} with your requests."
            ) {
                CoastTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        pasteNote = null
                    },
                    label = { Text("API key") },
                    placeholder = { Text(cloud.keyCopy.placeholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    // A password field for the keyboard too, shown or hidden: it must not learn or suggest the key.
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canSave) save(input) }),
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    // After Paste the user is asked to check the whole key, which takes seeing it.
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                imageVector = if (keyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (keyVisible) "Hide API key" else "Show API key"
                            )
                        }
                    }
                )
                pasteNote?.let { note ->
                    Text(
                        note,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                // Paste beside Save, not under it: the two are done one after the other, and Save is the main one.
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    CoastSecondaryButton(
                        onClick = {
                            val copied = clipboard.getText()?.text.orEmpty()
                            if (copied.isBlank()) {
                                pasteNote = "Nothing is copied yet. Copy the key in step 2 first."
                            } else {
                                input = copied
                                pasteNote = "Pasted. Check that it is the whole key, then tap Save."
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = ButtonHeight)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Paste")
                    }
                    CoastButton(
                        onClick = { save(input) },
                        enabled = canSave,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = ButtonHeight)
                    ) {
                        Text("Save")
                    }
                }
            }

            if (provider == TranslationProvider.Gemini) {
                Text(FREE_GEMINI_KEY_WARNING, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * One step of [KeySetupSheet]: its number in a sunlit circle, what to do, a line about it, and the control
 * for it, if the step has one. The number is read out with the step, so it isn't announced on its own.
 */
@Composable
private fun SetupStep(number: Int, title: String, detail: String, content: @Composable ColumnScope.() -> Unit = {}) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .background(Sunlit, CircleShape)
                .clearAndSetSemantics {}
        ) {
            Text("$number", color = OnSunlit, style = MaterialTheme.typography.labelLarge)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .padding(top = 3.dp)
                    .semantics { contentDescription = "Step $number: $title" }
            )
            Text(detail, color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
            content()
        }
    }
}
