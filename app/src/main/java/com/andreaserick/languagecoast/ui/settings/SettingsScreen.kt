package com.andreaserick.languagecoast.ui.settings

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andreaserick.languagecoast.data.Languages
import com.andreaserick.languagecoast.data.ReminderSettings
import com.andreaserick.languagecoast.data.SettingsDefaults
import com.andreaserick.languagecoast.notifications.RANDOM_WINDOW_END_HOUR
import com.andreaserick.languagecoast.notifications.RANDOM_WINDOW_START_HOUR
import com.andreaserick.languagecoast.ui.components.LanguageField
import com.andreaserick.languagecoast.ui.components.ScreenHeader
import com.andreaserick.languagecoast.ui.components.SelectionDropdown
import com.andreaserick.languagecoast.ui.theme.CardWash
import com.andreaserick.languagecoast.ui.theme.DeepOceanBlue
import com.andreaserick.languagecoast.ui.theme.MistWhite
import com.andreaserick.languagecoast.ui.theme.SandBeige
import com.andreaserick.languagecoast.ui.theme.SandMuted
import com.andreaserick.languagecoast.ui.theme.WaveTeal
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The "Settings" screen, grouped into cards: languages, AI (Gemini model and API key),
 * study reminders, exporting / importing cards and app info.
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Settings")

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            SettingsCard(title = "Languages", icon = Icons.Default.Translate) {
                FieldLabel("Native language")
                LanguageField(
                    language = uiState.nativeLanguage,
                    languages = uiState.nativeLanguageOptions,
                    onPick = { viewModel.setNativeLanguage(it.name) },
                    pickerTitle = "Native Language",
                    pickerSubtitle = "Languages you study on a coast aren't listed."
                        .takeIf { uiState.nativeLanguageOptions.size < Languages.ALL.size }
                )
                Hint("The language you think in. Each coast sets the language you're learning.")
            }

            SettingsCard(title = "AI translation", icon = Icons.Default.AutoAwesome) {
                FieldLabel("Gemini model")
                SelectionDropdown(
                    options = SettingsDefaults.GEMINI_MODELS,
                    selected = uiState.geminiModel,
                    onSelected = viewModel::setGeminiModel
                )
                Spacer(modifier = Modifier.height(16.dp))
                ApiKeySection(uiState = uiState, onSave = viewModel::saveApiKey, onEdited = viewModel::onApiKeyEdited)
            }

            SettingsCard(title = "Study reminders", icon = Icons.Default.NotificationsActive) {
                ReminderSection(
                    reminder = uiState.reminder,
                    onEnabledChange = viewModel::setRemindersEnabled,
                    onTimeChange = viewModel::setReminderTime
                )
            }

            SettingsCard(title = "Your cards", icon = Icons.Default.SwapVert) {
                CardTransferSection()
            }

            SettingsCard(title = "About", icon = Icons.Default.Info) {
                val context = LocalContext.current
                Text("Language Coast ${remember { appVersion(context) }}", color = Color.White)
                Hint("Translations by Google Gemini · Dictionary lookups by dict.cc")
            }
        }
    }
}

/** A titled group of settings. */
@Composable
private fun SettingsCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardWash, contentColor = Color.White),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                Icon(icon, contentDescription = null, tint = SandBeige, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.size(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = SandBeige)
            }
            content()
        }
    }
}

@Composable
internal fun FieldLabel(text: String) {
    Text(text, color = MistWhite, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
internal fun Hint(text: String) {
    Text(text, color = MistWhite, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
internal fun coastSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = DeepOceanBlue,
    checkedTrackColor = SandBeige,
    uncheckedThumbColor = SandMuted,
    uncheckedTrackColor = Color.Transparent,
    uncheckedBorderColor = SandMuted
)

@Composable
internal fun coastChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color.Transparent,
    labelColor = SandBeige,
    selectedContainerColor = SandBeige,
    selectedLabelColor = DeepOceanBlue
)

@Composable
private fun ApiKeySection(uiState: SettingsUiState, onSave: (String) -> Unit, onEdited: () -> Unit) {
    var currentInput by remember(uiState.savedApiKey) { mutableStateOf(uiState.savedApiKey) }
    var passwordVisible by remember { mutableStateOf(false) }
    val isSaved = uiState.savedApiKey.isNotEmpty() && currentInput.trim() == uiState.savedApiKey

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Google Gemini API key", color = MistWhite, fontSize = 14.sp, modifier = Modifier.weight(1f))
        AssistChip(
            onClick = {},
            enabled = false,
            label = {
                Text(
                    when {
                        isSaved -> "Key saved"
                        uiState.savedApiKey.isEmpty() -> "No key yet"
                        else -> "Unsaved changes"
                    }
                )
            },
            leadingIcon = if (isSaved) {
                { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else null,
            colors = AssistChipDefaults.assistChipColors(
                disabledContainerColor = if (isSaved) SandBeige else Color.Transparent,
                disabledLabelColor = if (isSaved) DeepOceanBlue else SandBeige,
                disabledLeadingIconContentColor = DeepOceanBlue
            ),
            border = if (isSaved) null else AssistChipDefaults.assistChipBorder(enabled = false, disabledBorderColor = SandMuted)
        )
    }
    Hint("Get a free key from Google AI Studio. It stays on this device.")
    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = currentInput,
        onValueChange = {
            currentInput = it
            onEdited()
        },
        label = { Text("API Key") },
        placeholder = { Text("AIzaSy...") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (passwordVisible) "Hide API key" else "Show API key"
                )
            }
        }
    )

    Spacer(modifier = Modifier.height(12.dp))

    Button(
        onClick = { onSave(currentInput) },
        enabled = !isSaved && currentInput.isNotBlank(),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        Text(if (uiState.apiKeySaved) "Saved" else "Save API Key", fontSize = 16.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderSection(
    reminder: ReminderSettings,
    onEnabledChange: (Boolean) -> Unit,
    onTimeChange: (LocalTime?) -> Unit
) {
    val context = LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Daily reminder", color = Color.White)
            Text(
                text = when {
                    !reminder.enabled -> "Off"
                    reminder.time == null -> "At a surprise time between $RANDOM_WINDOW_START_HOUR:00 and ${RANDOM_WINDOW_END_HOUR + 1}:00"
                    else -> "Every day at ${formatTime(context, reminder.time)}"
                },
                color = MistWhite,
                fontSize = 13.sp
            )
        }
        Switch(checked = reminder.enabled, onCheckedChange = onEnabledChange, colors = coastSwitchColors())
    }

    if (reminder.enabled) {
        Spacer(modifier = Modifier.height(12.dp))
        val chipColors = coastChipColors()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = reminder.time == null,
                onClick = { onTimeChange(null) },
                label = { Text("Surprise me") },
                colors = chipColors
            )
            FilterChip(
                selected = reminder.time != null,
                onClick = { showTimePicker = true },
                label = { Text(reminder.time?.let { formatTime(context, it) } ?: "Pick a time") },
                colors = chipColors
            )
        }
    }

    if (showTimePicker) {
        val initial = reminder.time ?: DEFAULT_REMINDER_TIME
        val state = rememberTimePickerState(
            initialHour = initial.hour,
            initialMinute = initial.minute,
            is24Hour = DateFormat.is24HourFormat(context)
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = DeepOceanBlue,
            titleContentColor = SandBeige,
            title = { Text("Reminder time") },
            text = {
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = WaveTeal,
                        clockDialSelectedContentColor = DeepOceanBlue,
                        clockDialUnselectedContentColor = Color.White,
                        selectorColor = SandBeige,
                        containerColor = DeepOceanBlue,
                        periodSelectorBorderColor = SandMuted,
                        periodSelectorSelectedContainerColor = SandBeige,
                        periodSelectorSelectedContentColor = DeepOceanBlue,
                        periodSelectorUnselectedContainerColor = Color.Transparent,
                        periodSelectorUnselectedContentColor = SandBeige,
                        timeSelectorSelectedContainerColor = SandBeige,
                        timeSelectorSelectedContentColor = DeepOceanBlue,
                        timeSelectorUnselectedContainerColor = WaveTeal,
                        timeSelectorUnselectedContentColor = Color.White
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChange(LocalTime.of(state.hour, state.minute))
                    showTimePicker = false
                }) { Text("Set", color = SandBeige) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel", color = SandBeige) }
            }
        )
    }
}

private fun formatTime(context: Context, time: LocalTime): String =
    if (DateFormat.is24HourFormat(context)) time.format(DateTimeFormatter.ofPattern("HH:mm"))
    else time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

private fun appVersion(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        ?.let { "v$it" } ?: ""
