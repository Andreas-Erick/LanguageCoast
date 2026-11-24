package com.andreaserick.languagecoast.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andreaserick.languagecoast.data.Languages
import com.andreaserick.languagecoast.data.LocalAiCatalog
import com.andreaserick.languagecoast.data.Pace
import com.andreaserick.languagecoast.data.Profile
import com.andreaserick.languagecoast.data.ReminderSettings
import com.andreaserick.languagecoast.data.SettingsDefaults
import com.andreaserick.languagecoast.data.cloud
import com.andreaserick.languagecoast.data.localAiAvailable
import com.andreaserick.languagecoast.data.onDeviceTranslationAvailable
import com.andreaserick.languagecoast.data.profilePreview
import com.andreaserick.languagecoast.notifications.RANDOM_WINDOW_END_HOUR
import com.andreaserick.languagecoast.notifications.RANDOM_WINDOW_START_HOUR
import com.andreaserick.languagecoast.ui.components.ButtonHeight
import com.andreaserick.languagecoast.ui.components.CoastSecondaryButton
import com.andreaserick.languagecoast.ui.components.ContentCard
import com.andreaserick.languagecoast.ui.components.LanguageField
import com.andreaserick.languagecoast.ui.components.LocalReducedMotion
import com.andreaserick.languagecoast.ui.components.LocalUndoMessenger
import com.andreaserick.languagecoast.ui.components.PaceOptions
import com.andreaserick.languagecoast.ui.components.ScreenHeader
import com.andreaserick.languagecoast.ui.components.ScreenMargin
import com.andreaserick.languagecoast.ui.components.Support
import com.andreaserick.languagecoast.ui.components.appVersion
import com.andreaserick.languagecoast.ui.components.coastChipBorder
import com.andreaserick.languagecoast.ui.components.coastChipColors
import com.andreaserick.languagecoast.ui.components.formatTime
import com.andreaserick.languagecoast.ui.components.is24HourClock
import com.andreaserick.languagecoast.ui.components.rememberSendFeedback
import com.andreaserick.languagecoast.ui.theme.CoralText
import com.andreaserick.languagecoast.ui.theme.SkyClock
import com.andreaserick.languagecoast.ui.theme.SunlitText
import com.andreaserick.languagecoast.ui.theme.SurfaceRaised
import com.andreaserick.languagecoast.ui.theme.TextPrimary
import com.andreaserick.languagecoast.ui.theme.TextSecondary
import com.andreaserick.languagecoast.ui.theme.ThemeMode
import com.andreaserick.languagecoast.ui.theme.rememberSunLocationAccess
import com.andreaserick.languagecoast.ui.theme.sunLocationAvailable
import com.andreaserick.languagecoast.ui.theme.tabular
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.koin.compose.viewmodel.koinViewModel

/** [SettingsScreen]'s `focus` that opens it at the Translation card, from anywhere that needs an AI provider set up. */
const val SETTINGS_FOCUS_TRANSLATION = "translation"

/** [SettingsScreen]'s `focus` that opens it at the Backup card, from Today's "Back up your cards once". */
const val SETTINGS_FOCUS_BACKUP = "backup"

/**
 * The "Settings" screen, grouped into cards (About you opens its own screen through [onAboutYou]): help,
 * languages, how cards are translated (on the phone or by an online AI with the user's own key), study
 * reminders, exporting / importing cards, backup and app info.
 *
 * @param focus A card to open the screen at, or null for the top. With [SETTINGS_FOCUS_TRANSLATION] it
 * scrolls to the Translation card once, and if the provider picked is an online AI without a key yet (an
 * iPhone starts with Gemini and no key), opens the guided key setup too: whoever sent the user here wants
 * that done, and the bare key field asks too much of someone who has never made a key. With
 * [SETTINGS_FOCUS_BACKUP] it scrolls to the Backup card.
 */
@Composable
fun SettingsScreen(onAboutYou: () -> Unit, focus: String? = null, viewModel: SettingsViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    // Where the Translation card starts in the scrolled column, once it has been laid out.
    var translationTop by remember { mutableStateOf<Int?>(null) }
    var backupTop by remember { mutableStateOf<Int?>(null) }
    // Done once per visit, so coming back from a turn of the phone or from the key's web page doesn't jump again.
    var focusHandled by rememberSaveable(focus) { mutableStateOf(focus == null) }
    var showKeySetup by rememberSaveable { mutableStateOf(false) }
    var showHowToUse by rememberSaveable { mutableStateOf(false) }
    var showLicences by rememberSaveable { mutableStateOf(false) }
    // The local model whose delete button was tapped, until the user confirms or backs out.
    var modelToDelete by rememberSaveable { mutableStateOf<String?>(null) }

    if (!focusHandled) {
        val reducedMotion = LocalReducedMotion.current
        LaunchedEffect(focus) {
            if (focus == SETTINGS_FOCUS_TRANSLATION) {
                // The provider and keys start as placeholders: decide only once the stored ones have been read.
                val loaded = snapshotFlow { uiState }.first { it.loaded }
                val top = snapshotFlow { translationTop }.filterNotNull().first()
                if (reducedMotion) scrollState.scrollTo(top) else scrollState.animateScrollTo(top)
                if (loaded.cloudKeyMissing) showKeySetup = true
            } else if (focus == SETTINGS_FOCUS_BACKUP) {
                val top = snapshotFlow { backupTop }.filterNotNull().first()
                if (reducedMotion) scrollState.scrollTo(top) else scrollState.animateScrollTo(top)
            }
            focusHandled = true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Settings",
            subtitle = "Languages, AI provider and study preferences"
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
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

            SettingsCard(
                title = "Translation",
                icon = Icons.Default.AutoAwesome,
                modifier = Modifier.onGloballyPositioned { translationTop = it.positionInParent().y.roundToInt() }
            ) {
                TranslationProviderSection(
                    uiState = uiState,
                    viewModel = viewModel,
                    onDeleteModel = { modelToDelete = it },
                    onSetUpKey = { showKeySetup = true }
                )
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
                PaceSection(viewModel)
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
                            label = { Text("$goal", style = MaterialTheme.typography.labelMedium.tabular) },
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
                if (uiState.themeMode == ThemeMode.Sun) {
                    SunSection(viewModel)
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

                SettingsCard(
                    title = "Backup",
                    icon = Icons.Default.Backup,
                    modifier = Modifier.onGloballyPositioned { backupTop = it.positionInParent().y.roundToInt() }
                ) {
                    BackupSection()
                }
            }

            SettingsCard(title = "About", icon = Icons.Default.Info) {
                AboutSection(onShowLicences = { showLicences = true })
            }
        }
    }

    if (showHowToUse) HowToUseSheet(onDismiss = { showHowToUse = false })
    // Only while an online AI is picked: switching to one on the phone leaves nothing to set up.
    val keySetupFor = uiState.translationProvider.cloud
    if (showKeySetup && keySetupFor != null) {
        KeySetupSheet(cloud = keySetupFor, uiState = uiState, viewModel = viewModel, onDismiss = { showKeySetup = false })
    }
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
private fun PaceSection(viewModel: SettingsViewModel) {
    // No placeholder pace: showing Steady for a frame would make a Fast or Sprint pick jump.
    val pace by viewModel.pace.collectAsState()
    pace?.let { current -> PaceOptions(pace = current, waiting = 0, onPick = viewModel::setPace) }
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
    Text("Language Coast $version", color = TextPrimary, style = MaterialTheme.typography.titleSmall)
    Hint(
        "Online AI by Google Gemini, OpenAI, Anthropic Claude, Groq, DeepSeek and OpenRouter, each with your own key." +
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
private fun SettingsCard(title: String, icon: ImageVector, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    ContentCard(modifier = modifier, padding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 18.dp)) {
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
internal fun NoticeBox(text: String, action: String, onAction: () -> Unit) {
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
 * What Follow the sun is doing now, and the switch that lets it go by the phone's approximate location
 * instead of its time zone. Switching it on asks for the permission if it is missing.
 */
@Composable
private fun SunSection(viewModel: SettingsViewModel) {
    val (usesLocation, location) = viewModel.sunLocation.collectAsState().value
    val access = rememberSunLocationAccess()
    val active = usesLocation && access.allowed && location != null
    val sky = remember(com.andreaserick.languagecoast.ui.theme.palette, active) {
        SkyClock.skyAt(Clock.System.now(), TimeZone.currentSystemDefault(), location.takeIf { active })
    }
    Hint(
        "Sunrise, midday, evening and sunset at the real times of the sun where you are, blending into each " +
            "other, and black at night. Now: ${sky.label}."
    )
    if (!sunLocationAvailable) return
    Spacer(modifier = Modifier.height(12.dp))
    ToggleRow(
        title = "Exact sun times from your location",
        subtitle = when {
            !usesLocation -> "Off: going by the phone's time zone, which can be an hour out at its edges."
            !access.allowed -> "Waiting for permission. Until then: the time zone."
            location == null -> "Waiting for the phone to find where it is. Until then: the time zone."
            else -> "Going by your approximate location. It stays on this phone and is never sent anywhere."
        },
        checked = usesLocation,
        onCheckedChange = { enabled ->
            viewModel.setSunUsesLocation(enabled)
            // The moment to ask: the user has just said they want it.
            if (enabled && !access.allowed) access.request()
        }
    )
    if (usesLocation && !access.allowed) {
        Spacer(modifier = Modifier.height(10.dp))
        NoticeBox(
            text = "Location isn't allowed for Language Coast, so the sun's times come from the time zone.",
            action = "Allow location",
            onAction = access.request
        )
    }
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
                label = { Text(reminder.time?.let { formatTime(it) } ?: "Pick a time", style = MaterialTheme.typography.labelMedium.tabular) },
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
                // window, such as a phone on its side or half of a split screen, gets the fields to type in. So does a
                // narrow dialog, where a 12-hour clock's hour, minute and AM/PM (about 280 dp) would be squeezed.
                BoxWithConstraints {
                    if (maxHeight < 380.dp || maxWidth < 280.dp) TimeInput(state = state) else TimePicker(state = state)
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
