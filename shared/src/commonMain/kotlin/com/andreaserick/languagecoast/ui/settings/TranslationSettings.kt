package com.andreaserick.languagecoast.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.andreaserick.languagecoast.data.CLOUD_PROVIDERS
import com.andreaserick.languagecoast.data.CloudProvider
import com.andreaserick.languagecoast.data.DEFAULT_CATEGORY
import com.andreaserick.languagecoast.data.Languages
import com.andreaserick.languagecoast.data.SettingsDefaults
import com.andreaserick.languagecoast.data.TranslationProvider
import com.andreaserick.languagecoast.data.localAiAvailable
import com.andreaserick.languagecoast.data.onDeviceLanguage
import com.andreaserick.languagecoast.data.onDeviceTranslationAvailable
import com.andreaserick.languagecoast.ui.components.ButtonHeight
import com.andreaserick.languagecoast.ui.components.CoastButton
import com.andreaserick.languagecoast.ui.components.CoastTextField
import com.andreaserick.languagecoast.ui.components.Pill
import com.andreaserick.languagecoast.ui.components.SelectionDropdown
import com.andreaserick.languagecoast.ui.components.coastChipBorder
import com.andreaserick.languagecoast.ui.components.coastChipColors
import com.andreaserick.languagecoast.ui.theme.CoralText
import com.andreaserick.languagecoast.ui.theme.Seafoam
import com.andreaserick.languagecoast.ui.theme.Sunlit
import com.andreaserick.languagecoast.ui.theme.SunlitText
import com.andreaserick.languagecoast.ui.theme.SurfaceRaised
import com.andreaserick.languagecoast.ui.theme.TextPrimary
import com.andreaserick.languagecoast.ui.theme.TextSecondary

// The Translation card of Settings: the provider picked, each cloud provider's model and key.

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
 * @param onWalkThrough Opens the guided setup, offered next to "Get a key" while no key is saved; null for none.
 */
@Composable
internal fun ApiKeySection(
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
    check: KeyCheckState? = null,
    onWalkThrough: (() -> Unit)? = null
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
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            SettingsLink("Get ${if (shortNoun.first().lowercaseChar() in "aeiou") "an" else "a"} $shortNoun") { openLink(getUrl) }
            // For someone who has never made a key: the same page, with each step on it said in turn.
            if (onWalkThrough != null && savedKey.isEmpty()) SettingsLink("Walk me through it", onClick = onWalkThrough)
        }
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
        // Kept apart from the save button so removing never reads as part of saving.
        Spacer(modifier = Modifier.height(8.dp))
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
 * @param onWalkThrough Opens the guided key setup ([KeySetupSheet]) for [provider].
 */
@Composable
internal fun CloudKeySection(
    provider: TranslationProvider,
    title: String,
    intro: String,
    placeholder: String,
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onWalkThrough: (() -> Unit)? = null
) {
    val savedKey = uiState.savedKey(provider)
    val allowed = uiState.sharingAllowed(provider)
    val saveKey = rememberCloudKeySave(provider, uiState, viewModel)
    var askToAllow by rememberSaveable(provider) { mutableStateOf(false) }

    ApiKeySection(
        title = title,
        hint = "$intro It is stored only on this device and sent only to ${provider.company} with your requests.",
        placeholder = placeholder,
        savedKey = savedKey,
        justSaved = uiState.keyJustSaved(provider),
        onSave = saveKey,
        onEdited = { viewModel.onKeyEdited(provider) },
        onRemove = { viewModel.removeKey(provider) },
        warning = FREE_GEMINI_KEY_WARNING.takeIf { provider == TranslationProvider.Gemini },
        getUrl = provider.keyUrl,
        check = uiState.keyCheck.takeIf { it.provider == provider },
        onWalkThrough = onWalkThrough
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

/**
 * How a typed cloud key is saved, wherever it is typed (the Translation card, [KeySetupSheet]): at once if
 * text may already go to [provider], otherwise after the permission dialog, whose "Allow and save" stores
 * both. The dialog is shown from here, so every place that saves a key asks the same way.
 *
 * @param onSaved Called once the key is stored, not when the user backs out of the dialog.
 * @return Saves the key it is given.
 */
@Composable
internal fun rememberCloudKeySave(
    provider: TranslationProvider,
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onSaved: () -> Unit = {}
): (String) -> Unit {
    val allowed = uiState.sharingAllowed(provider)
    // The key typed in, while the dialog asks for permission. A secret, so it isn't kept across a restart.
    var keyAwaitingPermission by remember(provider) { mutableStateOf<String?>(null) }
    keyAwaitingPermission?.let { key ->
        AiSharingSaveDialog(
            provider = provider,
            onAllow = {
                viewModel.saveKey(provider, key, allowSharing = true)
                keyAwaitingPermission = null
                onSaved()
            },
            onDismiss = { keyAwaitingPermission = null }
        )
    }
    return { key ->
        if (allowed) {
            viewModel.saveKey(provider, key)
            onSaved()
        } else {
            keyAwaitingPermission = key
        }
    }
}

/**
 * The provider a newcomer is pointed to: its key is free to start with and takes a couple of minutes to
 * make, so the app can do everything before the user has paid anyone anything.
 */
internal val RECOMMENDED_PROVIDER = TranslationProvider.Gemini

/** What the chip of [RECOMMENDED_PROVIDER] says after its name. */
private const val RECOMMENDED_NOTE = "Recommended · free to start"

/** The chips of a group of providers, with the picked one filled and the recommended one marked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProviderChips(providers: List<TranslationProvider>, picked: TranslationProvider, onPick: (TranslationProvider) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        providers.forEach { provider ->
            FilterChip(
                selected = provider == picked,
                onClick = { onPick(provider) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(provider.label, style = MaterialTheme.typography.labelMedium)
                        if (provider == RECOMMENDED_PROVIDER) {
                            Spacer(modifier = Modifier.width(6.dp))
                            // Quieter than the name, in the chip's own colour, so it reads right picked or not.
                            Text(
                                RECOMMENDED_NOTE,
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalContentColor.current.copy(alpha = 0.78f)
                            )
                        }
                    }
                },
                colors = coastChipColors(),
                border = coastChipBorder(enabled = true, selected = provider == picked),
                shape = MaterialTheme.shapes.small
            )
        }
    }
}

/**
 * Where translations come from, and how the picked provider is set up: what runs on the phone for free
 * (on-device translation, a Local AI model), or an online AI with the user's own API key. The model of an
 * online AI is folded away under "Advanced: choose model": the default fits nearly everyone, and the list of
 * model ids is the most technical thing on the card.
 *
 * @param onSetUpKey Opens the guided key setup for the picked online AI.
 */
@Composable
internal fun TranslationProviderSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onDeleteModel: (String) -> Unit,
    onSetUpKey: () -> Unit = {}
) {
    val onThePhone = remember {
        listOfNotNull(
            TranslationProvider.OnDevice.takeIf { onDeviceTranslationAvailable },
            TranslationProvider.LocalAi.takeIf { localAiAvailable }
        )
    }
    val cloud = remember { TranslationProvider.entries.filter { it.isCloud } }
    var modelOpen by rememberSaveable { mutableStateOf(false) }
    if (onThePhone.isNotEmpty()) {
        FieldLabel("On this phone, free")
        ProviderChips(onThePhone, uiState.translationProvider, viewModel::setTranslationProvider)
        Spacer(modifier = Modifier.height(12.dp))
    }
    FieldLabel("Online AI (free or paid account)")
    ProviderChips(cloud, uiState.translationProvider, viewModel::setTranslationProvider)
    Hint(
        "An online AI needs an API key: a long code you create in your own account with that provider. It lets this app send " +
            "requests in your name. ${RECOMMENDED_PROVIDER.label} is free to start; with the others you may pay for what you use. " +
            "Providers set their own minimum age for a key. Google, for example, requires 18 or older."
    )
    Spacer(modifier = Modifier.height(10.dp))

    when (uiState.translationProvider) {
        TranslationProvider.OnDevice -> {
            if (onDeviceTranslationAvailable) {
                val unsupported = remember { Languages.ALL.filter { onDeviceLanguage(it.name) == null }.map { it.name } }
                Hint(
                    "Translations by Google Translate, on your phone. Free and private, and works offline. " +
                        "Each language is downloaded once (about 30 MB). " +
                        "It only translates, so cards go on the island named under Topic, or \"$DEFAULT_CATEGORY\"."
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
        else -> {
            val cloud = CLOUD_PROVIDERS.getValue(uiState.translationProvider)
            Spacer(modifier = Modifier.height(8.dp))
            CloudKeySection(
                provider = cloud.provider,
                title = cloud.keyCopy.title,
                intro = cloud.keyCopy.intro,
                placeholder = cloud.keyCopy.placeholder,
                uiState = uiState,
                viewModel = viewModel,
                onWalkThrough = onSetUpKey
            )
            Spacer(modifier = Modifier.height(8.dp))
            val model = uiState.model(cloud.provider)
            CollapsibleRow(
                title = "Advanced: choose model",
                subtitle = modelSummary(cloud, model),
                expanded = modelOpen,
                onToggle = { modelOpen = !modelOpen }
            ) {
                if (cloud.hasModelList) {
                    ModelPicker("${uiState.translationProvider.label} model", cloud.provider, model, { viewModel.setModel(cloud.provider, it) }, uiState, viewModel::checkForNewModels)
                } else {
                    TypedModelField(cloud = cloud, model = model, onChange = { viewModel.setModel(cloud.provider, it) })
                }
                cloud.modelHint?.let { Hint(it) }
            }
        }
    }
}

/**
 * The model in use, in a line under the folded "Advanced: choose model". A provider that picks the model
 * itself when left at its default (OpenRouter) is said in words, not by the id that asks for that.
 */
private fun modelSummary(cloud: CloudProvider, model: String): String {
    val inUse = cloud.modelOrDefault(model)
    return if (!cloud.hasModelList && inUse == cloud.defaultModel) "Now: ${cloud.provider.label} chooses" else "Now: $inUse"
}

/** The model of a cloud provider without a list (OpenRouter), typed by its id; blank means [CloudProvider.defaultModel]. */
@Composable
internal fun TypedModelField(cloud: CloudProvider, model: String, onChange: (String) -> Unit) {
    FieldLabel("Model")
    var typed by remember(model) { mutableStateOf(model) }
    CoastTextField(
        value = typed,
        onValueChange = {
            typed = it
            onChange(it)
        },
        placeholder = { Text(cloud.defaultModel) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            // The placeholder goes once something is typed; this keeps the field's name for screen readers.
            .semantics { contentDescription = "Model" }
    )
}

/**
 * The model of a cloud provider: the list to pick from, and "Refresh model list", which asks the provider
 * what it offers now, adds the new models to the list and removes the ones that are gone.
 *
 * @param selected The model in use; shown even if a list from an earlier version doesn't have it.
 */
@Composable
internal fun ModelPicker(
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
        onSelected = onSelected,
        modifier = Modifier.semantics { contentDescription = label }
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
        Text(if (checking) "Asking ${provider.label}…" else "Refresh model list")
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
