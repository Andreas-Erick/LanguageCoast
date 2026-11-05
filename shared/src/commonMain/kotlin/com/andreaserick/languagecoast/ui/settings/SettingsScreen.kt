package com.andreaserick.languagecoast.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import com.andreaserick.languagecoast.ui.theme.ThemeMode
import com.andreaserick.languagecoast.data.LocalAiCatalog
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NotificationsActive
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andreaserick.languagecoast.data.DEFAULT_CATEGORY
import com.andreaserick.languagecoast.data.DeviceHardwareProfile
import com.andreaserick.languagecoast.data.Languages
import com.andreaserick.languagecoast.data.Profile
import com.andreaserick.languagecoast.data.profileExample
import com.andreaserick.languagecoast.data.profilePreview
import com.andreaserick.languagecoast.data.profileShortLabel
import com.andreaserick.languagecoast.data.ModelFitLevel
import com.andreaserick.languagecoast.data.ModelInstallStatus
import com.andreaserick.languagecoast.data.ModelRecommendation
import com.andreaserick.languagecoast.data.ReminderSettings
import com.andreaserick.languagecoast.data.SettingsDefaults
import com.andreaserick.languagecoast.data.TranslationProvider
import com.andreaserick.languagecoast.data.localAiAvailable
import com.andreaserick.languagecoast.data.onDeviceLanguage
import com.andreaserick.languagecoast.data.onDeviceTranslationAvailable
import com.andreaserick.languagecoast.notifications.RANDOM_WINDOW_END_HOUR
import com.andreaserick.languagecoast.notifications.RANDOM_WINDOW_START_HOUR
import com.andreaserick.languagecoast.ui.components.ButtonHeight
import com.andreaserick.languagecoast.ui.components.CoastButton
import com.andreaserick.languagecoast.ui.components.CoastSecondaryButton
import com.andreaserick.languagecoast.ui.components.CoastTextField
import com.andreaserick.languagecoast.ui.components.ContentCard
import com.andreaserick.languagecoast.ui.components.LanguageField
import com.andreaserick.languagecoast.ui.components.Pill
import com.andreaserick.languagecoast.ui.components.ScreenHeader
import com.andreaserick.languagecoast.ui.components.ScreenMargin
import com.andreaserick.languagecoast.ui.components.SelectionDropdown
import com.andreaserick.languagecoast.ui.components.appVersion
import com.andreaserick.languagecoast.ui.components.coastChipBorder
import com.andreaserick.languagecoast.ui.components.coastChipColors
import com.andreaserick.languagecoast.ui.components.formatTime
import com.andreaserick.languagecoast.ui.components.is24HourClock
import com.andreaserick.languagecoast.ui.theme.CoralText
import com.andreaserick.languagecoast.ui.theme.OnSunlit
import com.andreaserick.languagecoast.ui.theme.Seafoam
import com.andreaserick.languagecoast.ui.theme.SurfBlue
import com.andreaserick.languagecoast.ui.theme.Sunlit
import com.andreaserick.languagecoast.ui.theme.SurfaceRaised
import com.andreaserick.languagecoast.ui.theme.SurfaceSelected
import com.andreaserick.languagecoast.ui.theme.TextPrimary
import com.andreaserick.languagecoast.ui.theme.TextSecondary
import kotlin.math.roundToInt
import kotlinx.datetime.LocalTime
import org.koin.compose.viewmodel.koinViewModel

/**
 * The "Settings" screen, grouped into cards: languages, AI & translation hub
 * (with Local AI hardware scanner & model installer plus multi-cloud providers),
 * study reminders, exporting / importing cards, backup, how-to-use guide and app info.
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showHowToUse by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Settings",
            subtitle = "Languages, AI engines & study preferences"
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenMargin, vertical = 12.dp)
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

            SettingsCard(title = "Translation", icon = Icons.Default.AutoAwesome) {
                TranslationProviderSection(uiState = uiState, viewModel = viewModel)
            }

            SettingsCard(title = "Study reminders", icon = Icons.Default.NotificationsActive) {
                ReminderSection(
                    reminder = uiState.reminder,
                    onEnabledChange = viewModel::setRemindersEnabled,
                    onTimeChange = viewModel::setReminderTime
                )
            }

            SettingsCard(title = "About you", icon = Icons.Default.Person) {
                ProfileSection(profile = uiState.profile, onChange = viewModel::setProfileField)
            }

            SettingsCard(title = "Weekly goal", icon = Icons.Default.Flag) {
                FieldLabel("Islands to study each week")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsDefaults.WEEKLY_GOALS.forEach { goal ->
                        FilterChip(
                            selected = goal == uiState.weeklyGoal,
                            onClick = { viewModel.setWeeklyGoal(goal) },
                            label = { Text("$goal", style = MaterialTheme.typography.labelMedium) },
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    subtitle = "A soft tone for right and wrong answers.",
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

            SettingsCard(title = "About", icon = Icons.Default.Info) {
                Text("Language Coast ${appVersion()}", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Hint("Powered by Local LiteRT-LM, Google Gemini, OpenAI, Claude, Groq, DeepSeek & OpenRouter · Dictionary lookups by dict.cc")
                val uriHandler = LocalUriHandler.current
                TextButton(
                    onClick = { runCatching { uriHandler.openUri(PRIVACY_POLICY_URL) } },
                    colors = ButtonDefaults.textButtonColors(contentColor = Sunlit),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Privacy policy", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    if (showHowToUse) HowToUseSheet(onDismiss = { showHowToUse = false })

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
                    Text("Download anyway", color = Sunlit, style = MaterialTheme.typography.labelMedium)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissMeteredWarning) {
                    Text("Wait for Wi-Fi", color = TextPrimary, style = MaterialTheme.typography.labelMedium)
                }
            }
        )
    }
}

/** A setting that is on or off, with a title and a line explaining it. */
@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Where the privacy policy is published; the app stores link to it too. */
private const val PRIVACY_POLICY_URL =
    "https://github.com/Andreas-Erick/LanguageCoast/blob/main/docs/privacy-policy.md"

/** A titled group of settings. */
/**
 * The user's name, city and so on: the blanks starter sentences use, entered once here and filled in
 * wherever one appears. Each field is saved as it is typed.
 */
@Composable
private fun ProfileSection(profile: Profile, onChange: (placeholder: String, value: String) -> Unit) {
    Hint("Starter sentences have blanks like [name] and [city]. Fill these in once and they're filled in for you. Leave out what you'd rather not share.")
    val fillers = profile.fillers()

    @Composable
    fun Field(placeholder: String, modifier: Modifier) {
        val saved = fillers[placeholder].orEmpty()
        // Typing is kept locally so the field doesn't lag behind the saved value. Until the user types, the
        // field follows what is saved (which arrives from disk after the first composition); after that, saved
        // values only echo the user's own keystrokes and are ignored, so a slow write can't eat a letter.
        var draft by remember(placeholder) { mutableStateOf(saved) }
        var typed by remember(placeholder) { mutableStateOf(false) }
        LaunchedEffect(saved) { if (!typed) draft = saved }
        CoastTextField(
            value = draft,
            onValueChange = {
                typed = true
                draft = it
                onChange(placeholder, it)
            },
            label = { Text(profileShortLabel(placeholder)) },
            placeholder = { Text(profileExample(placeholder)) },
            singleLine = true,
            modifier = modifier
        )
    }

    @Composable
    fun Pair(heading: String, first: String, second: String) {
        FieldLabel(heading)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Field(first, Modifier.weight(1f))
            Field(second, Modifier.weight(1f))
        }
    }

    Pair("You", "name", "job")
    Pair("Where you live", "city", "country")
    Pair("Where you're from", "home country", "hometown")

    profilePreview(profile)?.let { preview ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .background(SurfaceRaised, MaterialTheme.shapes.small)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text("How your sentences will read", style = MaterialTheme.typography.labelMedium, color = Sunlit)
            Text(preview, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun SettingsCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    ContentCard(padding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 14.dp)) {
            Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.size(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
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

/**
 * An API key field with a "saved" status chip and a Save button.
 *
 * @param savedKey The key currently saved, or "" if none.
 * @param justSaved Whether the key was saved in this visit and not edited since, to say "Saved" on the button.
 */
@Composable
private fun ApiKeySection(
    title: String,
    hint: String,
    placeholder: String,
    savedKey: String,
    justSaved: Boolean,
    onSave: (String) -> Unit,
    onEdited: () -> Unit
) {
    var currentInput by remember(savedKey) { mutableStateOf(savedKey) }
    var passwordVisible by remember { mutableStateOf(false) }
    val isSaved = savedKey.isNotEmpty() && currentInput.trim() == savedKey

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = TextSecondary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
        Pill(
            text = when {
                isSaved -> "Key saved"
                savedKey.isEmpty() -> "No key yet"
                else -> "Unsaved changes"
            },
            container = SurfaceRaised,
            content = if (isSaved) Seafoam else TextSecondary
        )
    }
    Hint(hint)
    Spacer(modifier = Modifier.height(12.dp))

    CoastTextField(
        value = currentInput,
        onValueChange = {
            currentInput = it
            onEdited()
        },
        label = { Text("API Key") },
        placeholder = { Text(placeholder) },
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

    CoastButton(
        onClick = { onSave(currentInput) },
        enabled = !isSaved && currentInput.isNotBlank(),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ButtonHeight)
    ) {
        Text(if (justSaved) "Saved" else "Save API Key")
    }
}

/** How each translation provider is set up: on-device dictionary, Local AI LLM hub, or cloud AI providers. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TranslationProviderSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    FieldLabel("Translate with")
    val providers = remember {
        TranslationProvider.entries.filter { provider ->
            when (provider) {
                TranslationProvider.OnDevice -> onDeviceTranslationAvailable
                TranslationProvider.LocalAi -> localAiAvailable
                else -> true
            }
        }
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        providers.forEach { provider ->
            FilterChip(
                selected = provider == uiState.translationProvider,
                onClick = { viewModel.setTranslationProvider(provider) },
                label = { Text(provider.label, style = MaterialTheme.typography.labelMedium) },
                colors = coastChipColors(),
                border = coastChipBorder(enabled = true, selected = provider == uiState.translationProvider),
                shape = MaterialTheme.shapes.small
            )
        }
    }
    Spacer(modifier = Modifier.height(10.dp))

    when (uiState.translationProvider) {
        TranslationProvider.OnDevice -> {
            val unsupported = remember { Languages.ALL.filter { onDeviceLanguage(it.name) == null }.map { it.name } }
            Hint(
                "Free and private, and works offline. Each language is downloaded once (about 30 MB). " +
                    "It only translates, so cards go into the category you type, or \"$DEFAULT_CATEGORY\"."
            )
            if (unsupported.isNotEmpty()) Hint("Not available for ${unsupported.joinToString(", ")}.")
        }
        TranslationProvider.LocalAi -> {
            LocalAiHubSection(uiState = uiState, viewModel = viewModel)
        }
        TranslationProvider.Gemini -> {
            Spacer(modifier = Modifier.height(8.dp))
            ModelPicker("Gemini model", TranslationProvider.Gemini, uiState.geminiModel, viewModel::setGeminiModel, uiState, viewModel::checkForNewModels)
            Hint(
                "gemini-3.5-flash-lite answers in under a second and is plenty for translations, island sorting and alternatives. " +
                    "The other models think before they answer, so they take a few seconds longer."
            )
            Spacer(modifier = Modifier.height(16.dp))
            ApiKeySection(
                title = "Google Gemini API key",
                hint = "Get a free key from Google AI Studio (aistudio.google.com). It stays on this device.",
                placeholder = "AIzaSy...",
                savedKey = uiState.savedApiKey,
                justSaved = uiState.apiKeySaved,
                onSave = viewModel::saveApiKey,
                onEdited = viewModel::onApiKeyEdited
            )
        }
        TranslationProvider.OpenAI -> {
            Spacer(modifier = Modifier.height(8.dp))
            ModelPicker("OpenAI model", TranslationProvider.OpenAI, uiState.openAiModel, viewModel::setOpenAiModel, uiState, viewModel::checkForNewModels)
            Spacer(modifier = Modifier.height(16.dp))
            ApiKeySection(
                title = "OpenAI API key",
                hint = "Create a key on platform.openai.com/api-keys. The key is stored only on this device.",
                placeholder = "sk-proj-...",
                savedKey = uiState.savedOpenAiKey,
                justSaved = uiState.openAiSaved,
                onSave = viewModel::saveOpenAiKey,
                onEdited = viewModel::onOpenAiKeyEdited
            )
        }
        TranslationProvider.Claude -> {
            Spacer(modifier = Modifier.height(8.dp))
            ModelPicker("Claude model", TranslationProvider.Claude, uiState.claudeModel, viewModel::setClaudeModel, uiState, viewModel::checkForNewModels)
            Spacer(modifier = Modifier.height(16.dp))
            ApiKeySection(
                title = "Anthropic Claude API key",
                hint = "Create a key on console.anthropic.com. Great for nuanced, idiomatic translations.",
                placeholder = "sk-ant-...",
                savedKey = uiState.savedClaudeKey,
                justSaved = uiState.claudeSaved,
                onSave = viewModel::saveClaudeKey,
                onEdited = viewModel::onClaudeKeyEdited
            )
        }
        TranslationProvider.Groq -> {
            Spacer(modifier = Modifier.height(8.dp))
            ModelPicker("Groq model", TranslationProvider.Groq, uiState.groqModel, viewModel::setGroqModel, uiState, viewModel::checkForNewModels)
            Hint("Ultra-fast LPU inference with Llama 3.3 and Qwen.")
            Spacer(modifier = Modifier.height(16.dp))
            ApiKeySection(
                title = "Groq API key",
                hint = "Get an API key at console.groq.com/keys. Stored locally on this device.",
                placeholder = "gsk_...",
                savedKey = uiState.savedGroqKey,
                justSaved = uiState.groqSaved,
                onSave = viewModel::saveGroqKey,
                onEdited = viewModel::onGroqKeyEdited
            )
        }
        TranslationProvider.DeepSeek -> {
            Spacer(modifier = Modifier.height(8.dp))
            ModelPicker("DeepSeek model", TranslationProvider.DeepSeek, uiState.deepSeekModel, viewModel::setDeepSeekModel, uiState, viewModel::checkForNewModels)
            Spacer(modifier = Modifier.height(16.dp))
            ApiKeySection(
                title = "DeepSeek API key",
                hint = "Create a key on platform.deepseek.com. Stored locally on this device.",
                placeholder = "sk-...",
                savedKey = uiState.savedDeepSeekKey,
                justSaved = uiState.deepSeekSaved,
                onSave = viewModel::saveDeepSeekKey,
                onEdited = viewModel::onDeepSeekKeyEdited
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
                modifier = Modifier.fillMaxWidth()
            )
            Hint("\"${SettingsDefaults.OPENROUTER_MODEL}\" picks a model for you. Find other model IDs on openrouter.ai/models.")
            Spacer(modifier = Modifier.height(16.dp))
            ApiKeySection(
                title = "OpenRouter API key",
                hint = "Create a key on openrouter.ai. Requests are paid from your OpenRouter credits. The key stays on this device.",
                placeholder = "sk-or-...",
                savedKey = uiState.savedOpenRouterKey,
                justSaved = uiState.openRouterSaved,
                onSave = viewModel::saveOpenRouterKey,
                onEdited = viewModel::onOpenRouterKeyEdited
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
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * Hardware scanner and local `.litertlm` model recommender & installer for on-device LLM inference.
 */
@Composable
private fun LocalAiHubSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    Hint(
        "Run a generative AI model 100% offline on your phone using Google LiteRT-LM. " +
            "Unlike basic offline dictionary packs, Local AI models also categorize cards into islands, pick emojis and suggest natural alternatives — with zero API keys or cloud costs."
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
                .height(48.dp)
        ) {
            Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Scan Phone Hardware")
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    FieldLabel("Recommended Local AI Models")

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
                onDelete = { viewModel.deleteLocalModel(model.id) }
            )
        }
    }

    if (uiState.modelRecommendations.any { it.model.gated }) {
        Spacer(modifier = Modifier.height(20.dp))
        ApiKeySection(
            title = "Hugging Face token",
            hint = "Only for the gated models: accept the model's licence on huggingface.co, then create a read token " +
                "under Settings \u2192 Access Tokens. It is sent to Hugging Face only when downloading.",
            placeholder = "hf_...",
            savedKey = uiState.savedHuggingFaceToken,
            justSaved = uiState.huggingFaceTokenSaved,
            onSave = viewModel::saveHuggingFaceToken,
            onEdited = viewModel::onHuggingFaceTokenEdited
        )
    }
}

/** Displays the live hardware scan results of the user's phone. */
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
                        text = "${profile.socOrProcessor} · ${profile.cpuCores} cores (${profile.primaryAbi})",
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
                    text = "RAM: ${profile.availableRamGbText} free of ${profile.totalRamGbText}",
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
                Icon(
                    Icons.Default.Speed,
                    contentDescription = null,
                    tint = if (profile.gpuAccelerationSupported) Seafoam else TextSecondary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (profile.gpuAccelerationSupported) "OpenCL GPU Ready" else "CPU Inference",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (profile.gpuAccelerationSupported) Seafoam else TextSecondary,
                    fontWeight = FontWeight.Medium
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
                    Icon(Icons.Default.Radar, contentDescription = null, tint = Sunlit, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isScanning) "Scanning..." else "Re-scan", color = Sunlit, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** A card for a single recommended local `.litertlm` model with install, progress, activate and delete controls. */
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
                        text = "${model.family} · ${recommendation.recommendedBackend} · ${model.parameterCount} · ${model.sizeLabel} · ${model.minRamLabel}",
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
                            else -> "Download \u00B7 ${model.sizeLabel}"
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
                                    style = MaterialTheme.typography.labelSmall,
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
                                    text = "Installed & Active for Local AI",
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
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Use This Installed Model")
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
                    DownloadButton(label = "Try again \u00B7 ${model.sizeLabel}", enabled = true, primary = true, onClick = onInstall)
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
        ModelFitLevel.NOT_RECOMMENDED -> SurfaceSelected to CoralText
    }
    Pill(text = fitLevel.label, container = container, content = content)
}

/** Turns the daily reminder on or off, and picks a random time ("Surprise me") or a fixed one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderSection(
    reminder: ReminderSettings,
    onEnabledChange: (Boolean) -> Unit,
    onTimeChange: (LocalTime?) -> Unit
) {
    var showTimePicker by remember { mutableStateOf(false) }
    val time = reminder.time

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Daily reminder", style = MaterialTheme.typography.titleSmall)
            Text(
                text = when {
                    !reminder.enabled -> "Off"
                    time == null -> "At a surprise time between $RANDOM_WINDOW_START_HOUR:00 and ${RANDOM_WINDOW_END_HOUR + 1}:00"
                    else -> "Every day at ${formatTime(time)}"
                },
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Switch(checked = reminder.enabled, onCheckedChange = onEnabledChange)
    }

    if (reminder.enabled) {
        Spacer(modifier = Modifier.height(12.dp))
        val chipColors = coastChipColors()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                label = { Text(reminder.time?.let { formatTime(it) } ?: "Pick a time", style = MaterialTheme.typography.labelMedium) },
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
                TimePicker(state = state)
            },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChange(LocalTime(state.hour, state.minute))
                    showTimePicker = false
                }) { Text("Set", color = Sunlit, style = MaterialTheme.typography.labelMedium) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel", color = TextPrimary, style = MaterialTheme.typography.labelMedium) }
            }
        )
    }
}
