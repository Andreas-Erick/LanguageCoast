package com.andreaserick.languagecoast.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andreaserick.languagecoast.data.DEFAULT_CATEGORY
import com.andreaserick.languagecoast.data.DeviceHardwareProfile
import com.andreaserick.languagecoast.data.Languages
import com.andreaserick.languagecoast.data.LocalAiCatalog
import com.andreaserick.languagecoast.data.ModelFitLevel
import com.andreaserick.languagecoast.data.ModelInstallStatus
import com.andreaserick.languagecoast.data.ModelRecommendation
import com.andreaserick.languagecoast.data.Pace
import com.andreaserick.languagecoast.data.Profile
import com.andreaserick.languagecoast.data.ReminderSettings
import com.andreaserick.languagecoast.data.SettingsDefaults
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.data.TranslationProvider
import com.andreaserick.languagecoast.data.localAiAvailable
import com.andreaserick.languagecoast.data.onDeviceLanguage
import com.andreaserick.languagecoast.data.onDeviceTranslationAvailable
import com.andreaserick.languagecoast.data.profilePreview
import com.andreaserick.languagecoast.notifications.RANDOM_WINDOW_END_HOUR
import com.andreaserick.languagecoast.notifications.RANDOM_WINDOW_START_HOUR
import com.andreaserick.languagecoast.ui.components.ButtonHeight
import com.andreaserick.languagecoast.ui.components.CoastButton
import com.andreaserick.languagecoast.ui.components.CoastSecondaryButton
import com.andreaserick.languagecoast.ui.components.CoastTextField
import com.andreaserick.languagecoast.ui.components.ContentCard
import com.andreaserick.languagecoast.ui.components.LanguageField
import com.andreaserick.languagecoast.ui.components.LocalUndoMessenger
import com.andreaserick.languagecoast.ui.components.PaceOptions
import com.andreaserick.languagecoast.ui.components.Pill
import com.andreaserick.languagecoast.ui.components.ScreenHeader
import com.andreaserick.languagecoast.ui.components.ScreenMargin
import com.andreaserick.languagecoast.ui.components.SelectionDropdown
import com.andreaserick.languagecoast.ui.components.Support
import com.andreaserick.languagecoast.ui.components.appVersion
import com.andreaserick.languagecoast.ui.components.coastChipBorder
import com.andreaserick.languagecoast.ui.components.coastChipColors
import com.andreaserick.languagecoast.ui.components.formatTime
import com.andreaserick.languagecoast.ui.components.is24HourClock
import com.andreaserick.languagecoast.ui.components.rememberSendFeedback
import com.andreaserick.languagecoast.ui.theme.CoralText
import com.andreaserick.languagecoast.ui.theme.OnSunlit
import com.andreaserick.languagecoast.ui.theme.Seafoam
import com.andreaserick.languagecoast.ui.theme.SurfBlue
import com.andreaserick.languagecoast.ui.theme.Sunlit
import com.andreaserick.languagecoast.ui.theme.SunlitText
import com.andreaserick.languagecoast.ui.theme.SurfaceRaised
import com.andreaserick.languagecoast.ui.theme.SurfaceSelected
import com.andreaserick.languagecoast.ui.theme.TextPrimary
import com.andreaserick.languagecoast.ui.theme.TextSecondary
import com.andreaserick.languagecoast.ui.theme.ThemeMode
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * The "Settings" screen, grouped into cards (About you opens its own screen through [onAboutYou]): help,
 * languages, how cards are translated (on the phone or by a cloud AI with the user's own key), study
 * reminders, exporting / importing cards, backup and app info.
 */
@Composable
fun SettingsScreen(onAboutYou: () -> Unit, viewModel: SettingsViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showHowToUse by rememberSaveable { mutableStateOf(false) }
    var showLicences by rememberSaveable { mutableStateOf(false) }
    // The local model whose delete button was tapped, until the user confirms or backs out.
    var modelToDelete by rememberSaveable { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Settings",
            subtitle = "Languages, AI provider and study preferences"
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenMargin, vertical = 12.dp)
        ) {
            SettingsCard(title = "Help", icon = Icons.AutoMirrored.Filled.HelpOutline) {
                CoastSecondaryButton(
                    onClick = { showHowToUse = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ButtonHeight)
                ) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("How to use")
                }
                Hint("A short guide to coasts, creating cards, AI providers and studying.")
            }

            SettingsCard(title = "Languages", icon = Icons.Default.Translate) {
                FieldLabel("Native language")
                LanguageField(
                    language = uiState.nativeLanguage,
                    languages = uiState.nativeLanguageOptions,
                    onPick = { viewModel.setNativeLanguage(it.name) },
                    pickerTitle = "Native language",
                    pickerSubtitle = "Languages you study on a coast aren't listed."
                        .takeIf { uiState.nativeLanguageOptions.size < Languages.ALL.size },
                    label = "Native language"
                )
                Hint("The language you think in. Each coast sets the language you're learning.")
            }

            SettingsCard(title = "Translation", icon = Icons.Default.AutoAwesome) {
                TranslationProviderSection(uiState = uiState, viewModel = viewModel, onDeleteModel = { modelToDelete = it })
            }

            SettingsCard(title = "Study reminders", icon = Icons.Default.NotificationsActive) {
                ReminderSection(
                    reminder = uiState.reminder,
                    onEnabledChange = viewModel::setRemindersEnabled,
                    onTimeChange = viewModel::setReminderTime
                )
            }

            SettingsCard(title = "About you", icon = Icons.Default.Person) {
                ProfileSection(profile = uiState.profile, onOpen = onAboutYou)
            }

            SettingsCard(title = "Pace", icon = Icons.Default.Speed) {
                PaceSection()
            }

            SettingsCard(title = "Weekly goal", icon = Icons.Default.Flag) {
                FieldLabel("Islands to study each week")
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SettingsDefaults.WEEKLY_GOALS.forEach { goal ->
                        FilterChip(
                            selected = goal == uiState.weeklyGoal,
                            onClick = { viewModel.setWeeklyGoal(goal) },
                            label = { Text("$goal", style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")) },
                            colors = coastChipColors(),
                            border = coastChipBorder(enabled = true, selected = goal == uiState.weeklyGoal),
                            shape = MaterialTheme.shapes.small
                        )
                    }
                }
                Hint("Shown on My Coasts as a bar for the week. Finishing a session on an island counts it.")
            }

            SettingsCard(title = "Look & feel", icon = Icons.Default.Palette) {
                FieldLabel("Theme")
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = mode == uiState.themeMode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(mode.label, style = MaterialTheme.typography.labelMedium) },
                            colors = coastChipColors(),
                            border = coastChipBorder(enabled = true, selected = mode == uiState.themeMode),
                            shape = MaterialTheme.shapes.small
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                ToggleRow(
                    title = "Vibration",
                    subtitle = "A short buzz when a card is flipped or graded.",
                    checked = uiState.hapticsEnabled,
                    onCheckedChange = viewModel::setHapticsEnabled
                )
                Spacer(modifier = Modifier.height(10.dp))
                ToggleRow(
                    title = "Sounds",
                    subtitle = "A soft tone for right and wrong answers. It follows the ring volume and stays quiet on silent.",
                    checked = uiState.soundsEnabled,
                    onCheckedChange = viewModel::setSoundsEnabled
                )
            }

            if (fileTransferAvailable) {
                SettingsCard(title = "Your cards", icon = Icons.Default.SwapVert) {
                    CardTransferSection()
                }

                SettingsCard(title = "Backup", icon = Icons.Default.Backup) {
                    BackupSection()
                }
            }

            SettingsCard(title = "About", icon = Icons.Default.Info) {
                AboutSection(onShowLicences = { showLicences = true })
            }
        }
    }

    if (showHowToUse) HowToUseSheet(onDismiss = { showHowToUse = false })
    if (showLicences) LicencesSheet(onDismiss = { showLicences = false })

    uiState.meteredWarningModelId?.let { modelId ->
        val model = LocalAiCatalog.byId(modelId)
        AlertDialog(
            onDismissRequest = viewModel::dismissMeteredWarning,
            title = { Text("Download over mobile data?") },
            text = {
                Text(
                    "You're not on Wi-Fi. ${model?.name ?: "This model"} is ${model?.sizeLabel ?: "large"}, " +
                        "which may use up a good part of a data plan."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.installLocalModel(modelId, allowMetered = true) }) {
                    Text("Download anyway", color = SunlitText, style = MaterialTheme.typography.labelMedium)
                }
            },
            dismissButton = {
                // Nothing is queued for later, so the button doesn't promise it.
                TextButton(onClick = viewModel::dismissMeteredWarning) {
                    Text("Not now", color = TextPrimary, style = MaterialTheme.typography.labelMedium)
                }
            }
        )
    }

    modelToDelete?.let { modelId ->
        val model = LocalAiCatalog.byId(modelId)
        AlertDialog(
            onDismissRequest = { modelToDelete = null },
            title = { Text("Delete ${model?.name ?: "this model"}?") },
            text = { Text("This frees ${model?.sizeLabel ?: "its space"} on your phone. You can download the model again later.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteLocalModel(modelId)
                    modelToDelete = null
                }) { Text("Delete", color = CoralText, style = MaterialTheme.typography.labelMedium) }
            },
            dismissButton = {
                TextButton(onClick = { modelToDelete = null }) {
                    Text("Cancel", color = TextPrimary, style = MaterialTheme.typography.labelMedium)
                }
            }
        )
    }
}

/** How much new a day's session brings, picked from the three paces and stored at once. */
@Composable
private fun PaceSection() {
    val settings = koinInject<SettingsRepository>()
    val pace by settings.pace.collectAsState(initial = Pace.Steady)
    val scope = rememberCoroutineScope()
    PaceOptions(pace = pace, waiting = 0, onPick = { scope.launch { settings.setPace(it) } })
    Hint("How many new cards a day's session on Today introduces. Everything new comes back for review, so a faster pace means longer sessions for weeks. Miss a few days and the session shrinks by itself.")
}

/**
 * A setting that is on or off, with a title and a line explaining it. The whole row is the switch: a tap
 * anywhere on it flips it, and a screen reader says the title with the state.
 */
@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** The app's version, who its translations come from, and the ways to reach the developer and the small print. */
@Composable
private fun AboutSection(onShowLicences: () -> Unit) {
    val version = appVersion()
    val platformName = if (runsOnAndroid) "Android" else "iPhone"
    val openLink = rememberOpenLink()
    val sendFeedback = rememberSendFeedback()
    val messenger = LocalUndoMessenger.current
    Text("Language Coast $version", color = TextPrimary, fontWeight = FontWeight.SemiBold)
    Hint(
        "Cloud AI by Google Gemini, OpenAI, Anthropic Claude, Groq, DeepSeek and OpenRouter, each with your own key." +
            (if (localAiAvailable) " Local AI runs with Google LiteRT-LM." else "") +
            " Word lookups open dict.cc."
    )
    if (onDeviceTranslationAvailable) Hint("On-device translation powered by Google Translate.")
    Spacer(modifier = Modifier.height(4.dp))
    // Only the version and the platform go along: the message may end up on the public issue tracker.
    SettingsLink("Send feedback or report a problem") {
        val opened = sendFeedback("Language Coast feedback", "\n\n\n" + "Language Coast $version".trim() + " on $platformName")
        if (!opened) messenger.showMessage("Nothing on this phone can open that link.")
    }
    SettingsLink("Open-source licences", onClick = onShowLicences)
    SettingsLink("Privacy policy") { openLink(Support.PRIVACY_POLICY_URL) }
}

/**
 * What the app knows about the user, in short: how their starter sentences read, and the way to the
 * "About you" screen, where the basics and the questions behind the islands about them are filled in.
 */
@Composable
private fun ProfileSection(profile: Profile, onOpen: () -> Unit) {
    profilePreview(profile)?.let { preview ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .background(SurfaceRaised, MaterialTheme.shapes.small)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text("How your sentences will read", style = MaterialTheme.typography.labelMedium, color = SunlitText)
            Text(preview, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
    }
    CoastSecondaryButton(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ButtonHeight)
    ) {
        Text(if (profile.isEmpty) "Tell the app about you" else "Open About you")
    }
    Hint("Your name, family, work and free time. Starter sentences are filled in with them, and on a coast your AI provider writes whole islands about you from your answers.")
}

/** A titled group of settings. The title is a heading, so a screen reader can jump from card to card. */
@Composable
private fun SettingsCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    ContentCard(padding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 14.dp)) {
            Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.size(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        }
        content()
    }
}

/** A small label above a settings field. */
@Composable
internal fun FieldLabel(text: String) {
    Text(text, color = TextSecondary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 6.dp))
}

/** A short explanation below a settings field. */
@Composable
internal fun Hint(text: String) {
    Text(text, color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
}

/** A quiet link inside a settings card, lined up with the text around it. */
@Composable
internal fun SettingsLink(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = SunlitText),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

/** A function that opens a web address in the browser, and says so when nothing on the phone can open it. */
@Composable
internal fun rememberOpenLink(): (String) -> Unit {
    val uriHandler = LocalUriHandler.current
    val messenger = LocalUndoMessenger.current
    return remember<(String) -> Unit>(uriHandler, messenger) {
        { url ->
            try {
                uriHandler.openUri(url)
            } catch (e: Exception) {
                messenger.showMessage("Nothing on this phone can open that link.")
            }
        }
    }
}

/** Something in a settings card that needs the user's attention, with the one thing to do about it. */
@Composable
private fun NoticeBox(text: String, action: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceRaised, MaterialTheme.shapes.small)
            .padding(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 2.dp)
    ) {
        Text(text, color = TextPrimary, style = MaterialTheme.typography.bodySmall)
        SettingsLink(action, onClick = onAction)
    }
}

/**
 * A field for an API key or a token, with a "saved" status chip, the link to where one is made, a Save
 * button and, once one is saved, a way to remove it.
 *
 * @param savedKey The key currently saved, or "" if none.
 * @param justSaved Whether the key was saved in this visit and not edited since, to say "Saved" on the button.
 * @param noun What the secret is called in full ("API key", "token") and [shortNoun] in short ("key", "token").
 * @param warning A second line under [hint], for what the user should know before using such a key.
 * @param getUrl The page where the key or token is made, opened by "Get a key".
 * @param check How asking the provider whether it accepts the saved key went, if it was asked.
 */
@Composable
private fun ApiKeySection(
    title: String,
    hint: String,
    placeholder: String,
    savedKey: String,
    justSaved: Boolean,
    onSave: (String) -> Unit,
    onEdited: () -> Unit,
    onRemove: () -> Unit,
    noun: String = "API key",
    shortNoun: String = "key",
    warning: String? = null,
    getUrl: String? = null,
    check: KeyCheckState? = null
) {
    // Not kept across a restart of the app: a secret that isn't saved yet doesn't belong in saved state.
    var currentInput by remember(savedKey) { mutableStateOf(savedKey) }
    var passwordVisible by remember { mutableStateOf(false) }
    // Compared the way a key is cleaned when it is saved: a pasted key often has a space or line break inside.
    val isSaved = savedKey.isNotEmpty() && currentInput.filterNot { it.isWhitespace() || it.isISOControl() } == savedKey
    // A check that failed can be repeated: the key is saved, but whether it works is still unknown.
    val canCheckAgain = isSaved && check?.failed == true
    val openLink = rememberOpenLink()

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = TextSecondary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
        Pill(
            text = when {
                isSaved -> "${shortNoun.replaceFirstChar { it.uppercase() }} saved"
                savedKey.isEmpty() -> "No $shortNoun yet"
                else -> "Unsaved changes"
            },
            container = SurfaceRaised,
            content = if (isSaved) Seafoam else TextSecondary
        )
    }
    Hint(hint)
    if (warning != null) Hint(warning)
    if (getUrl != null) {
        SettingsLink("Get a $shortNoun") { openLink(getUrl) }
    } else {
        Spacer(modifier = Modifier.height(12.dp))
    }

    var confirmRemove by remember { mutableStateOf(false) }

    CoastTextField(
        value = currentInput,
        onValueChange = {
            currentInput = it
            onEdited()
        },
        label = { Text(noun.replaceFirstChar { it.uppercase() }) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        // A password field for the keyboard too, shown or hidden: it must not learn or suggest the key.
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onDone = { if ((!isSaved || canCheckAgain) && currentInput.isNotBlank()) onSave(currentInput) }
        ),
        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (passwordVisible) "Hide $noun" else "Show $noun"
                )
            }
        }
    )

    if (check != null && (check.checking || check.message != null)) {
        Text(
            text = if (check.checking) "Checking the $shortNoun…" else check.message.orEmpty(),
            color = if (check.failed) CoralText else TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .padding(top = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    CoastButton(
        onClick = { onSave(currentInput) },
        enabled = (!isSaved || canCheckAgain) && currentInput.isNotBlank(),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ButtonHeight)
    ) {
        Text(
            when {
                canCheckAgain -> "Check again"
                justSaved && isSaved -> "Saved"
                else -> "Save $noun"
            }
        )
    }

    if (savedKey.isNotEmpty()) {
        TextButton(
            onClick = { confirmRemove = true },
            colors = ButtonDefaults.textButtonColors(contentColor = CoralText),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("Remove $shortNoun", style = MaterialTheme.typography.labelMedium)
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove $noun?") },
            text = { Text("This deletes the saved $noun from this device.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onRemove()
                }) { Text("Remove", color = CoralText, style = MaterialTheme.typography.labelMedium) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) {
                    Text("Cancel", color = TextPrimary, style = MaterialTheme.typography.labelMedium)
                }
            }
        )
    }
}

/**
 * The API key of cloud [provider]. Saving one asks first for permission to send text to that provider,
 * unless it was given before; a key saved without it (by an earlier version of the app) gets an "Allow" row.
 * Below the key it says what the provider receives, for as long as the provider is picked.
 *
 * @param intro Where the key comes from, in a sentence; where it is kept is added to it.
 */
@Composable
private fun CloudKeySection(
    provider: TranslationProvider,
    title: String,
    intro: String,
    placeholder: String,
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val savedKey = uiState.savedKey(provider)
    val allowed = uiState.sharingAllowed(provider)
    // The key typed in, while the dialog asks for permission. A secret, so it isn't kept across a restart.
    var keyAwaitingPermission by remember(provider) { mutableStateOf<String?>(null) }
    var askToAllow by rememberSaveable(provider) { mutableStateOf(false) }

    ApiKeySection(
        title = title,
        hint = "$intro It is stored only on this device and sent only to ${provider.company} with your requests.",
        placeholder = placeholder,
        savedKey = savedKey,
        justSaved = uiState.keyJustSaved(provider),
        onSave = { key -> if (allowed) viewModel.saveKey(provider, key) else keyAwaitingPermission = key },
        onEdited = { viewModel.onKeyEdited(provider) },
        onRemove = { viewModel.removeKey(provider) },
        warning = FREE_GEMINI_KEY_WARNING.takeIf { provider == TranslationProvider.Gemini },
        getUrl = provider.keyUrl,
        check = uiState.keyCheck.takeIf { it.provider == provider }
    )

    if (savedKey.isNotEmpty() && !allowed) {
        Spacer(modifier = Modifier.height(12.dp))
        NoticeBox(
            text = "Nothing is sent to ${provider.label} yet. Translations and suggestions start once you allow it.",
            action = "Allow sending text to ${provider.label}",
            onAction = { askToAllow = true }
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
    AiSharingSummary(provider)

    keyAwaitingPermission?.let { key ->
        AiSharingSaveDialog(
            provider = provider,
            onAllow = {
                viewModel.saveKey(provider, key, allowSharing = true)
                keyAwaitingPermission = null
            },
            onDismiss = { keyAwaitingPermission = null }
        )
    }
    // Allowed from the app's own prompt while this one was open: nothing is left to ask, now or after the key is removed.
    if (askToAllow && allowed) SideEffect { askToAllow = false }
    if (askToAllow && !allowed) {
        AiSharingDialog(
            provider = provider,
            onAllow = {
                viewModel.allowSharing(provider)
                askToAllow = false
            },
            onDismiss = { askToAllow = false }
        )
    }
}

/** The chips of a group of providers, with the picked one filled. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderChips(providers: List<TranslationProvider>, picked: TranslationProvider, onPick: (TranslationProvider) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        providers.forEach { provider ->
            FilterChip(
                selected = provider == picked,
                onClick = { onPick(provider) },
                label = { Text(provider.label, style = MaterialTheme.typography.labelMedium) },
                colors = coastChipColors(),
                border = coastChipBorder(enabled = true, selected = provider == picked),
                shape = MaterialTheme.shapes.small
            )
        }
    }
}

/**
 * Where translations come from, and how the picked provider is set up: what runs on the phone for free
 * (on-device translation, a Local AI model), or a cloud AI with the user's own API key.
 */
@Composable
private fun TranslationProviderSection(uiState: SettingsUiState, viewModel: SettingsViewModel, onDeleteModel: (String) -> Unit) {
    val onThePhone = remember {
        listOfNotNull(
            TranslationProvider.OnDevice.takeIf { onDeviceTranslationAvailable },
            TranslationProvider.LocalAi.takeIf { localAiAvailable }
        )
    }
    val cloud = remember { TranslationProvider.entries.filter { it.isCloud } }
    if (onThePhone.isNotEmpty()) {
        FieldLabel("On this phone, free")
        ProviderChips(onThePhone, uiState.translationProvider, viewModel::setTranslationProvider)
        Spacer(modifier = Modifier.height(12.dp))
    }
    FieldLabel("Cloud AI, with your own key")
    ProviderChips(cloud, uiState.translationProvider, viewModel::setTranslationProvider)
    Hint(
        "A cloud AI needs an API key: a long code you create in your own account with that provider. It lets this app send " +
            "requests in your name, and the provider bills you for what you use. Providers set their own minimum age for a key. " +
            "Google, for example, requires 18 or older."
    )
    Spacer(modifier = Modifier.height(10.dp))

    when (uiState.translationProvider) {
        TranslationProvider.OnDevice -> {
            if (onDeviceTranslationAvailable) {
                val unsupported = remember { Languages.ALL.filter { onDeviceLanguage(it.name) == null }.map { it.name } }
                Hint(
                    "Translations by Google Translate, on your phone. Free and private, and works offline. " +
                        "Each language is downloaded once (about 30 MB). " +
                        "It only translates, so cards go on the island you type, or \"$DEFAULT_CATEGORY\"."
                )
                if (unsupported.isNotEmpty()) Hint("Not available for ${unsupported.joinToString(", ")}.")
            } else {
                Hint("On-device translation isn't available here. Pick another way to translate.")
            }
        }
        TranslationProvider.LocalAi -> {
            if (localAiAvailable) {
                LocalAiHubSection(uiState = uiState, viewModel = viewModel, onDeleteModel = onDeleteModel)
            } else {
                Hint("Local AI can't run here. Pick another way to translate.")
            }
        }
        TranslationProvider.Gemini -> {
            Spacer(modifier = Modifier.height(8.dp))
            ModelPicker("Gemini model", TranslationProvider.Gemini, uiState.geminiModel, viewModel::setGeminiModel, uiState, viewModel::checkForNewModels)
            Hint(
                "The default model answers in under a second and is plenty for translating, sorting cards onto islands and " +
                    "suggesting alternatives. The larger models think before they answer, so they take a few seconds longer."
            )
            Spacer(modifier = Modifier.height(16.dp))
            CloudKeySection(
                provider = TranslationProvider.Gemini,
                title = "Google Gemini API key",
                intro = "Google AI Studio gives you a key that is free to start with.",
                placeholder = "AIzaSy...",
                uiState = uiState,
                viewModel = viewModel
            )
        }
        TranslationProvider.OpenAI -> {
            Spacer(modifier = Modifier.height(8.dp))
            ModelPicker("OpenAI model", TranslationProvider.OpenAI, uiState.openAiModel, viewModel::setOpenAiModel, uiState, viewModel::checkForNewModels)
            Spacer(modifier = Modifier.height(16.dp))
            CloudKeySection(
                provider = TranslationProvider.OpenAI,
                title = "OpenAI API key",
                intro = "Create a key in your OpenAI account.",
                placeholder = "sk-proj-...",
                uiState = uiState,
                viewModel = viewModel
            )
        }
        TranslationProvider.Claude -> {
            Spacer(modifier = Modifier.height(8.dp))
            ModelPicker("Claude model", TranslationProvider.Claude, uiState.claudeModel, viewModel::setClaudeModel, uiState, viewModel::checkForNewModels)
            Spacer(modifier = Modifier.height(16.dp))
            CloudKeySection(
                provider = TranslationProvider.Claude,
                title = "Anthropic Claude API key",
                intro = "Create a key in your Anthropic account.",
                placeholder = "sk-ant-...",
                uiState = uiState,
                viewModel = viewModel
            )
        }
        TranslationProvider.Groq -> {
            Spacer(modifier = Modifier.height(8.dp))
            ModelPicker("Groq model", TranslationProvider.Groq, uiState.groqModel, viewModel::setGroqModel, uiState, viewModel::checkForNewModels)
            Hint("Groq runs open models such as Llama and Qwen, and answers quickly.")
            Spacer(modifier = Modifier.height(16.dp))
            CloudKeySection(
                provider = TranslationProvider.Groq,
                title = "Groq API key",
                intro = "Create a key in your Groq account.",
                placeholder = "gsk_...",
                uiState = uiState,
                viewModel = viewModel
            )
        }
        TranslationProvider.DeepSeek -> {
            Spacer(modifier = Modifier.height(8.dp))
            ModelPicker("DeepSeek model", TranslationProvider.DeepSeek, uiState.deepSeekModel, viewModel::setDeepSeekModel, uiState, viewModel::checkForNewModels)
            Spacer(modifier = Modifier.height(16.dp))
            CloudKeySection(
                provider = TranslationProvider.DeepSeek,
                title = "DeepSeek API key",
                intro = "Create a key in your DeepSeek account.",
                placeholder = "sk-...",
                uiState = uiState,
                viewModel = viewModel
            )
        }
        TranslationProvider.OpenRouter -> {
            Spacer(modifier = Modifier.height(8.dp))
            FieldLabel("Model")
            var model by remember(uiState.openRouterModel) { mutableStateOf(uiState.openRouterModel) }
            CoastTextField(
                value = model,
                onValueChange = {
                    model = it
                    viewModel.setOpenRouterModel(it)
                },
                placeholder = { Text(SettingsDefaults.OPENROUTER_MODEL) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    // The placeholder goes once something is typed; this keeps the field's name for screen readers.
                    .semantics { contentDescription = "Model" }
            )
            Hint("\"${SettingsDefaults.OPENROUTER_MODEL}\" picks a model for you. Find other model IDs on openrouter.ai/models.")
            Spacer(modifier = Modifier.height(16.dp))
            CloudKeySection(
                provider = TranslationProvider.OpenRouter,
                title = "OpenRouter API key",
                intro = "One key for models from many companies, paid from your OpenRouter credits.",
                placeholder = "sk-or-...",
                uiState = uiState,
                viewModel = viewModel
            )
        }
    }
}

/**
 * The model of a cloud provider: the list to pick from, and "Check for new models", which asks the provider
 * what it offers now, adds the new models to the list and removes the ones that are gone.
 *
 * @param selected The model in use; shown even if a list from an earlier version doesn't have it.
 */
@Composable
private fun ModelPicker(
    label: String,
    provider: TranslationProvider,
    selected: String,
    onSelected: (String) -> Unit,
    uiState: SettingsUiState,
    onCheck: () -> Unit
) {
    val options = uiState.modelOptions[provider] ?: SettingsDefaults.models(provider)
    FieldLabel(label)
    SelectionDropdown(
        options = if (selected in options) options else listOf(selected) + options,
        selected = selected,
        onSelected = onSelected
    )
    val check = uiState.modelCheck
    val checking = check.checking == provider
    // Less padding at the start than a text button has, so the icon lines up with the field above it.
    TextButton(
        onClick = onCheck,
        enabled = check.checking == null,
        colors = ButtonDefaults.textButtonColors(contentColor = SunlitText),
        contentPadding = PaddingValues(start = 4.dp, end = 12.dp),
        modifier = Modifier.padding(top = 2.dp)
    ) {
        if (checking) {
            CircularProgressIndicator(color = Sunlit, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        } else {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(if (checking) "Asking ${provider.label}…" else "Check for new models")
    }
    val message = check.message?.takeIf { check.provider == provider && !checking }
    if (message != null) {
        Text(
            message,
            color = if (check.failed) MaterialTheme.colorScheme.error else TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            // Read out when it arrives: the answer comes a moment after the tap, somewhere else on the screen.
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

/**
 * Local AI: what the phone can run, the models to download for it, and the Hugging Face token some of them need.
 *
 * @param onDeleteModel Asks to delete the installed model with that id; the screen confirms first.
 */
@Composable
private fun LocalAiHubSection(uiState: SettingsUiState, viewModel: SettingsViewModel, onDeleteModel: (String) -> Unit) {
    Hint(
        "An AI model that runs on your phone: free, private, and offline once it is downloaded. Unlike on-device " +
            "translation it also sorts cards onto islands and suggests other ways to say a sentence. " +
            "A small model is slower than a cloud AI and makes more mistakes."
    )
    Spacer(modifier = Modifier.height(14.dp))

    val profile = uiState.hardwareProfile
    if (profile != null) {
        HardwareProfileCard(
            profile = profile,
            isScanning = uiState.isScanningHardware,
            onRescan = viewModel::scanHardware
        )
    } else {
        CoastSecondaryButton(
            onClick = viewModel::scanHardware,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Check what this phone can run")
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    FieldLabel("Models for this phone")

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        uiState.modelRecommendations.forEach { recommendation ->
            val model = recommendation.model
            val installStatus = uiState.modelInstallStates[model.id] ?: ModelInstallStatus.NotInstalled
            val isActive = uiState.localAiModel == model.id
            ModelRecommendationCard(
                recommendation = recommendation,
                installStatus = installStatus,
                isActive = isActive,
                hasToken = uiState.savedHuggingFaceToken.isNotBlank(),
                onInstall = { viewModel.installLocalModel(model.id) },
                onCancel = { viewModel.cancelLocalModelInstall(model.id) },
                onSelect = { viewModel.setLocalAiModel(model.id) },
                onDelete = { onDeleteModel(model.id) }
            )
        }
    }

    if (uiState.modelRecommendations.any { it.model.gated }) {
        Spacer(modifier = Modifier.height(20.dp))
        ApiKeySection(
            title = "Hugging Face token",
            hint = "Only for models that need a token: accept the model's licence on huggingface.co, then create a read token " +
                "there (\"Get a token\" opens the page). It is stored only on this device and sent to Hugging Face only when downloading.",
            placeholder = "hf_...",
            savedKey = uiState.savedHuggingFaceToken,
            justSaved = uiState.huggingFaceTokenSaved,
            onSave = viewModel::saveHuggingFaceToken,
            onEdited = viewModel::onHuggingFaceTokenEdited,
            onRemove = viewModel::removeHuggingFaceToken,
            noun = "token",
            shortNoun = "token",
            getUrl = "https://huggingface.co/settings/tokens"
        )
    }
}

/** What the check of the phone found: its processor, memory and free storage, and what that is enough for. */
@Composable
private fun HardwareProfileCard(
    profile: DeviceHardwareProfile,
    isScanning: Boolean,
    onRescan: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceRaised, contentColor = TextPrimary),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.deviceName,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "${profile.socOrProcessor} · ${profile.cpuCores} cores",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Pill(text = profile.tier.label, container = SurfaceSelected, content = TextPrimary)
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = SurfaceSelected)
            Spacer(modifier = Modifier.height(12.dp))

            val ramRatio = if (profile.totalRamMb > 0) {
                ((profile.totalRamMb - profile.availableRamMb).toFloat() / profile.totalRamMb).coerceIn(0.05f, 0.95f)
            } else 0.5f

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Memory, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Memory: ${profile.availableRamGbText} free of ${profile.totalRamGbText}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { ramRatio },
                color = SurfBlue,
                trackColor = SurfaceSelected,
                gapSize = 0.dp,
                strokeCap = StrokeCap.Round,
                drawStopIndicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storage, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Free storage: ${profile.freeStorageGbText}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = profile.tier.summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onRescan,
                    enabled = !isScanning,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Radar, contentDescription = null, tint = SunlitText, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isScanning) "Checking…" else "Check again", color = SunlitText, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** A card for one local model: how well it fits the phone, and the download, its progress, "use" and delete. */
@Composable
private fun ModelRecommendationCard(
    recommendation: ModelRecommendation,
    installStatus: ModelInstallStatus,
    isActive: Boolean,
    hasToken: Boolean,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val model = recommendation.model
    val isBestMatch = recommendation.fitLevel == ModelFitLevel.BEST_MATCH
    val isInstalled = installStatus is ModelInstallStatus.Installed

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceRaised, contentColor = TextPrimary),
        border = if (isActive && isInstalled) BorderStroke(1.5.dp, Seafoam) else null,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            FitBadge(fitLevel = recommendation.fitLevel)

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        // What a learner can use: whose model it is, how big the download is, how much memory it needs.
                        text = "${model.family} · ${model.sizeLabel} · ${model.minRamLabel}",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (isInstalled) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete ${model.name}",
                            tint = CoralText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = model.summary,
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = recommendation.reason,
                color = if (recommendation.fitLevel == ModelFitLevel.NOT_RECOMMENDED) CoralText else TextSecondary,
                style = MaterialTheme.typography.labelSmall
            )

            Spacer(modifier = Modifier.height(12.dp))

            when (installStatus) {
                is ModelInstallStatus.NotInstalled -> {
                    val needsToken = model.gated && !hasToken
                    DownloadButton(
                        label = when {
                            needsToken -> "Needs a Hugging Face token"
                            !recommendation.hasEnoughStorage -> "Not enough free storage"
                            else -> "Download · ${model.sizeLabel}"
                        },
                        enabled = recommendation.hasEnoughStorage && !needsToken,
                        primary = isBestMatch,
                        onClick = onInstall
                    )
                }
                is ModelInstallStatus.Downloading -> {
                    val percent = (installStatus.progress * 100).roundToInt().coerceIn(0, 100)
                    val downloadedMb = installStatus.downloadedMb
                    val totalMb = installStatus.totalMb.coerceAtLeast(1L)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                CircularProgressIndicator(
                                    color = Sunlit,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Downloading $downloadedMb / $totalMb MB ($percent%)",
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(
                                onClick = onCancel,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = CoralText, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cancel", color = CoralText, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { installStatus.progress.coerceIn(0f, 1f) },
                            color = Sunlit,
                            trackColor = SurfaceSelected,
                            gapSize = 0.dp,
                            strokeCap = StrokeCap.Round,
                            drawStopIndicator = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                        )
                        // A download that stands still is most often this, and nothing else on the card says so.
                        Text(
                            text = "It waits while there is no Wi-Fi, unless you allowed mobile data.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
                is ModelInstallStatus.Installed -> {
                    if (isActive) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = SurfaceSelected,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Seafoam, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "In use for Local AI",
                                    color = Seafoam,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    } else {
                        CoastSecondaryButton(
                            onClick = onSelect,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Use this model")
                        }
                    }
                }
                is ModelInstallStatus.Failed -> {
                    Text(
                        text = installStatus.message,
                        color = CoralText,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    DownloadButton(label = "Try again · ${model.sizeLabel}", enabled = true, primary = true, onClick = onInstall)
                }
            }
        }
    }
}

/** The button that starts a model download: one line, never clipped, with the accent only for the best match. */
@Composable
private fun DownloadButton(label: String, enabled: Boolean, primary: Boolean, onClick: () -> Unit) {
    CoastButton(
        onClick = onClick,
        enabled = enabled,
        colors = if (primary) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColors(containerColor = SurfaceSelected, contentColor = TextPrimary),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
    ) {
        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FitBadge(fitLevel: ModelFitLevel) {
    val (container, content) = when (fitLevel) {
        ModelFitLevel.BEST_MATCH -> Sunlit to OnSunlit
        ModelFitLevel.GREAT_FIT -> SurfaceSelected to Seafoam
        ModelFitLevel.HEAVY_FIT -> SurfaceSelected to TextPrimary
        // Coral is too faint on this fill; the reason under the model's name carries the warning colour.
        ModelFitLevel.NOT_RECOMMENDED -> SurfaceSelected to TextPrimary
    }
    Pill(text = fitLevel.label, container = container, content = content)
}

/**
 * Turns the daily reminder on or off, and picks a random time ("Surprise me") or a fixed one. Turning it on
 * asks for the notification permission if it is missing, and while notifications are blocked it says so.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderSection(
    reminder: ReminderSettings,
    onEnabledChange: (Boolean) -> Unit,
    onTimeChange: (LocalTime?) -> Unit
) {
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    val notifications = rememberNotificationAccess()
    val time = reminder.time

    ToggleRow(
        title = "Daily reminder",
        subtitle = when {
            !reminder.enabled -> "Off"
            time == null -> "At a surprise time between ${formatTime(LocalTime(RANDOM_WINDOW_START_HOUR, 0))} " +
                "and ${formatTime(LocalTime(RANDOM_WINDOW_END_HOUR + 1, 0))}"
            else -> "Every day at ${formatTime(time)}"
        },
        checked = reminder.enabled,
        onCheckedChange = { enabled ->
            onEnabledChange(enabled)
            // The moment to ask: the user has just said they want reminders.
            if (enabled && !notifications.allowed) notifications.request()
        }
    )

    if (reminder.enabled && !notifications.allowed) {
        Spacer(modifier = Modifier.height(10.dp))
        NoticeBox(
            text = "Notifications are off for Language Coast, so reminders can't appear.",
            action = "Allow notifications",
            onAction = notifications.request
        )
    }

    if (reminder.enabled) {
        Spacer(modifier = Modifier.height(12.dp))
        val chipColors = coastChipColors()
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = reminder.time == null,
                onClick = { onTimeChange(null) },
                label = { Text("Surprise me", style = MaterialTheme.typography.labelMedium) },
                colors = chipColors,
                border = coastChipBorder(enabled = true, selected = reminder.time == null),
                shape = MaterialTheme.shapes.small
            )
            FilterChip(
                selected = reminder.time != null,
                onClick = { showTimePicker = true },
                label = { Text(reminder.time?.let { formatTime(it) } ?: "Pick a time", style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")) },
                colors = chipColors,
                border = coastChipBorder(enabled = true, selected = reminder.time != null),
                shape = MaterialTheme.shapes.small
            )
        }
    }

    if (showTimePicker) {
        val initial = reminder.time ?: DEFAULT_REMINDER_TIME
        val state = rememberTimePickerState(
            initialHour = initial.hour,
            initialMinute = initial.minute,
            is24Hour = is24HourClock()
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Reminder time") },
            text = {
                // The dial needs about 380 dp of height and can't scroll (dragging on it sets the time). A short
                // window, such as a phone on its side or half of a split screen, gets the fields to type in.
                BoxWithConstraints {
                    if (maxHeight < 380.dp) TimeInput(state = state) else TimePicker(state = state)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChange(LocalTime(state.hour, state.minute))
                    showTimePicker = false
                }) { Text("Set", color = SunlitText, style = MaterialTheme.typography.labelMedium) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel", color = TextPrimary, style = MaterialTheme.typography.labelMedium) }
            }
        )
    }
}
