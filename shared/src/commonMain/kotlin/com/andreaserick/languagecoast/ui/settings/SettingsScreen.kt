package com.andreaserick.languagecoast.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andreaserick.languagecoast.data.DEFAULT_CATEGORY
import com.andreaserick.languagecoast.data.DeviceHardwareProfile
import com.andreaserick.languagecoast.data.Languages
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
import com.andreaserick.languagecoast.ui.components.LanguageField
import com.andreaserick.languagecoast.ui.components.ScreenHeader
import com.andreaserick.languagecoast.ui.components.SelectionDropdown
import com.andreaserick.languagecoast.ui.components.appVersion
import com.andreaserick.languagecoast.ui.components.formatTime
import com.andreaserick.languagecoast.ui.components.is24HourClock
import com.andreaserick.languagecoast.ui.theme.CardBorder
import com.andreaserick.languagecoast.ui.theme.CardWash
import com.andreaserick.languagecoast.ui.theme.CoralText
import com.andreaserick.languagecoast.ui.theme.DeepOceanBlue
import com.andreaserick.languagecoast.ui.theme.ElevatedCardWash
import com.andreaserick.languagecoast.ui.theme.MistWhite
import com.andreaserick.languagecoast.ui.theme.SandBeige
import com.andreaserick.languagecoast.ui.theme.SandMuted
import com.andreaserick.languagecoast.ui.theme.SeafoamEmerald
import com.andreaserick.languagecoast.ui.theme.SunlitSand
import com.andreaserick.languagecoast.ui.theme.WaveTeal
import com.andreaserick.languagecoast.ui.theme.coastTextFieldColors
import kotlinx.datetime.LocalTime
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

/**
 * The "Settings" screen, grouped into glassmorphic cards: languages, AI & translation hub
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

            if (fileTransferAvailable) {
                SettingsCard(title = "Your cards", icon = Icons.Default.SwapVert) {
                    CardTransferSection()
                }

                SettingsCard(title = "Backup", icon = Icons.Default.Backup) {
                    BackupSection()
                }
            }

            SettingsCard(title = "Help", icon = Icons.AutoMirrored.Filled.HelpOutline) {
                OutlinedButton(
                    onClick = { showHowToUse = true },
                    border = BorderStroke(1.dp, SandBeige),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SandBeige),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("How to use", fontSize = 16.sp)
                }
                Hint("A short guide to coasts, creating cards, AI providers and studying.")
            }

            SettingsCard(title = "About", icon = Icons.Default.Info) {
                Text("Language Coast ${appVersion()}", color = Color.White, fontWeight = FontWeight.SemiBold)
                Hint("Powered by Local LiteRT-LM, Google Gemini, OpenAI, Claude, Groq, DeepSeek & OpenRouter · Dictionary lookups by dict.cc")
                val uriHandler = LocalUriHandler.current
                TextButton(
                    onClick = { runCatching { uriHandler.openUri(PRIVACY_POLICY_URL) } },
                    colors = ButtonDefaults.textButtonColors(contentColor = SandBeige),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Privacy policy")
                }
            }
        }
    }

    if (showHowToUse) HowToUseSheet(onDismiss = { showHowToUse = false })
}

/** Where the privacy policy is published; the app stores link to it too. */
private const val PRIVACY_POLICY_URL =
    "https://github.com/Andreas-Erick/LanguageCoast/blob/main/docs/privacy-policy.md"

/** A titled glassmorphic group of settings. */
@Composable
private fun SettingsCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardWash, contentColor = Color.White),
        border = BorderStroke(1.dp, CardBorder),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(34.dp)
                        .background(SandBeige.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                ) {
                    Icon(icon, contentDescription = null, tint = SandBeige, modifier = Modifier.size(19.dp))
                }
                Spacer(modifier = Modifier.size(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = SandBeige)
            }
            content()
        }
    }
}

/** A small label above a settings field. */
@Composable
internal fun FieldLabel(text: String) {
    Text(text, color = MistWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
}

/** A short explanation below a settings field. */
@Composable
internal fun Hint(text: String) {
    Text(text, color = MistWhite, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
}

/** Switch colors matching the app's sand and ocean theme. */
@Composable
internal fun coastSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = DeepOceanBlue,
    checkedTrackColor = SandBeige,
    uncheckedThumbColor = SandMuted,
    uncheckedTrackColor = Color.Transparent,
    uncheckedBorderColor = SandMuted
)

/** Filter chip colors matching the app's sand and ocean theme. */
@Composable
internal fun coastChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color.White.copy(alpha = 0.03f),
    labelColor = SandBeige,
    selectedContainerColor = SandBeige,
    selectedLabelColor = DeepOceanBlue
)

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
        Text(title, color = MistWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        AssistChip(
            onClick = {},
            enabled = false,
            label = {
                Text(
                    when {
                        isSaved -> "Key saved"
                        savedKey.isEmpty() -> "No key yet"
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
    Hint(hint)
    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = currentInput,
        onValueChange = {
            currentInput = it
            onEdited()
        },
        label = { Text("API Key") },
        placeholder = { Text(placeholder) },
        singleLine = true,
        colors = coastTextFieldColors(),
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
        Text(if (justSaved) "Saved" else "Save API Key", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
                label = { Text(provider.label, fontWeight = if (provider == uiState.translationProvider) FontWeight.Bold else FontWeight.Medium) },
                colors = coastChipColors()
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
            FieldLabel("Gemini model")
            SelectionDropdown(
                options = SettingsDefaults.GEMINI_MODELS,
                selected = uiState.geminiModel,
                onSelected = viewModel::setGeminiModel
            )
            Hint("gemini-2.5-flash is recommended for fast, natural translations, island categorization and alternative phrasings.")
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
            FieldLabel("OpenAI model")
            SelectionDropdown(
                options = SettingsDefaults.OPENAI_MODELS,
                selected = uiState.openAiModel,
                onSelected = viewModel::setOpenAiModel
            )
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
            FieldLabel("Claude model")
            SelectionDropdown(
                options = SettingsDefaults.CLAUDE_MODELS,
                selected = uiState.claudeModel,
                onSelected = viewModel::setClaudeModel
            )
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
            FieldLabel("Groq model")
            SelectionDropdown(
                options = SettingsDefaults.GROQ_MODELS,
                selected = uiState.groqModel,
                onSelected = viewModel::setGroqModel
            )
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
            FieldLabel("DeepSeek model")
            SelectionDropdown(
                options = SettingsDefaults.DEEPSEEK_MODELS,
                selected = uiState.deepSeekModel,
                onSelected = viewModel::setDeepSeekModel
            )
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
            OutlinedTextField(
                value = model,
                onValueChange = {
                    model = it
                    viewModel.setOpenRouterModel(it)
                },
                placeholder = { Text(SettingsDefaults.OPENROUTER_MODEL) },
                singleLine = true,
                colors = coastTextFieldColors(),
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
        OutlinedButton(
            onClick = viewModel::scanHardware,
            border = BorderStroke(1.dp, SandBeige),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = SandBeige),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Scan Phone Hardware", fontWeight = FontWeight.SemiBold)
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
                onInstall = { viewModel.installLocalModel(model.id) },
                onCancel = { viewModel.cancelLocalModelInstall(model.id) },
                onSelect = { viewModel.setLocalAiModel(model.id) },
                onDelete = { viewModel.deleteLocalModel(model.id) }
            )
        }
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
        colors = CardDefaults.cardColors(containerColor = ElevatedCardWash, contentColor = Color.White),
        border = BorderStroke(1.dp, SandBeige.copy(alpha = 0.28f)),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = SandBeige, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.deviceName,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "${profile.socOrProcessor} · ${profile.cpuCores} cores (${profile.primaryAbi})",
                        color = MistWhite,
                        fontSize = 12.sp
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = SandBeige.copy(alpha = 0.18f),
                    contentColor = SunlitSand
                ) {
                    Text(
                        text = profile.tier.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = CardBorder)
            Spacer(modifier = Modifier.height(12.dp))

            val ramRatio = if (profile.totalRamMb > 0) {
                ((profile.totalRamMb - profile.availableRamMb).toFloat() / profile.totalRamMb).coerceIn(0.05f, 0.95f)
            } else 0.5f

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Memory, contentDescription = null, tint = SandBeige, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "RAM: ${profile.availableRamGbText} free of ${profile.totalRamGbText}",
                    fontSize = 12.sp,
                    color = MistWhite,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { ramRatio },
                color = SandBeige,
                trackColor = Color.White.copy(alpha = 0.12f),
                strokeCap = StrokeCap.Round,
                drawStopIndicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storage, contentDescription = null, tint = SandBeige, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Free storage: ${profile.freeStorageGbText}",
                    fontSize = 12.sp,
                    color = MistWhite,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.Speed,
                    contentDescription = null,
                    tint = if (profile.gpuAccelerationSupported) SeafoamEmerald else SandMuted,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (profile.gpuAccelerationSupported) "OpenCL GPU Ready" else "CPU Inference",
                    fontSize = 12.sp,
                    color = if (profile.gpuAccelerationSupported) SeafoamEmerald else MistWhite,
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
                    fontSize = 12.sp,
                    color = SandMuted,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onRescan,
                    enabled = !isScanning,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Radar, contentDescription = null, tint = SandBeige, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isScanning) "Scanning..." else "Re-scan", color = SandBeige, fontSize = 12.sp)
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
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val model = recommendation.model
    val isBestMatch = recommendation.fitLevel == ModelFitLevel.BEST_MATCH
    val isInstalled = installStatus is ModelInstallStatus.Installed
    val borderColor = when {
        isActive && isInstalled -> SeafoamEmerald.copy(alpha = 0.7f)
        isBestMatch -> SandBeige.copy(alpha = 0.65f)
        else -> CardBorder
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isBestMatch) ElevatedCardWash else Color.White.copy(alpha = 0.04f),
            contentColor = Color.White
        ),
        border = BorderStroke(if (isBestMatch || (isActive && isInstalled)) 1.5.dp else 1.dp, borderColor),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                FitBadge(fitLevel = recommendation.fitLevel)
                Text(
                    text = "${model.parameterCount} · ${model.sizeLabel} · ${model.minRamLabel}",
                    color = SandMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = "${model.family} · ${recommendation.recommendedBackend}",
                        color = SandBeige,
                        fontSize = 12.sp,
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
                color = MistWhite,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = recommendation.reason,
                color = if (recommendation.fitLevel == ModelFitLevel.NOT_RECOMMENDED) CoralText else SandBeige,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            when (installStatus) {
                is ModelInstallStatus.NotInstalled -> {
                    Button(
                        onClick = onInstall,
                        enabled = recommendation.hasEnoughStorage,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isBestMatch) SandBeige else WaveTeal,
                            contentColor = if (isBestMatch) DeepOceanBlue else Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (recommendation.hasEnoughStorage) "Download & Install (${model.sizeLabel})" else "Not Enough Free Storage",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    color = SandBeige,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Installing... $downloadedMb / $totalMb MB ($percent%)",
                                    color = SandBeige,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            TextButton(
                                onClick = onCancel,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = CoralText, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cancel", color = CoralText, fontSize = 12.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { installStatus.progress.coerceIn(0f, 1f) },
                            color = SandBeige,
                            trackColor = Color.White.copy(alpha = 0.15f),
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
                            shape = RoundedCornerShape(12.dp),
                            color = SeafoamEmerald.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, SeafoamEmerald.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SeafoamEmerald, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Installed & Active for Local AI",
                                    color = SeafoamEmerald,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = onSelect,
                            border = BorderStroke(1.dp, SandBeige),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SandBeige),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Use This Installed Model", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                is ModelInstallStatus.Failed -> {
                    Text(
                        text = installStatus.message,
                        color = CoralText,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Button(
                        onClick = onInstall,
                        colors = ButtonDefaults.buttonColors(containerColor = SandBeige, contentColor = DeepOceanBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry Install (${model.sizeLabel})", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FitBadge(fitLevel: ModelFitLevel) {
    val (bg, fg) = when (fitLevel) {
        ModelFitLevel.BEST_MATCH -> SandBeige to DeepOceanBlue
        ModelFitLevel.GREAT_FIT -> SeafoamEmerald.copy(alpha = 0.2f) to SeafoamEmerald
        ModelFitLevel.HEAVY_FIT -> WaveTeal.copy(alpha = 0.35f) to Color.White
        ModelFitLevel.NOT_RECOMMENDED -> CoralText.copy(alpha = 0.2f) to CoralText
    }
    val prefix = if (fitLevel == ModelFitLevel.BEST_MATCH) "★ " else ""
    Surface(
        shape = RoundedCornerShape(50),
        color = bg,
        contentColor = fg
    ) {
        Text(
            text = prefix + fitLevel.label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
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
            Text("Daily reminder", color = Color.White, fontWeight = FontWeight.Medium)
            Text(
                text = when {
                    !reminder.enabled -> "Off"
                    time == null -> "At a surprise time between $RANDOM_WINDOW_START_HOUR:00 and ${RANDOM_WINDOW_END_HOUR + 1}:00"
                    else -> "Every day at ${formatTime(time)}"
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
                label = { Text(reminder.time?.let { formatTime(it) } ?: "Pick a time") },
                colors = chipColors
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
                    onTimeChange(LocalTime(state.hour, state.minute))
                    showTimePicker = false
                }) { Text("Set", color = SandBeige) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel", color = SandBeige) }
            }
        )
    }
}
