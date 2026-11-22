package com.andreaserick.languagecoast.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.andreaserick.languagecoast.data.TranslationProvider
import com.andreaserick.languagecoast.data.cloud
import com.andreaserick.languagecoast.ui.theme.SunlitText
import com.andreaserick.languagecoast.ui.theme.TextPrimary
import com.andreaserick.languagecoast.ui.theme.TextSecondary
import org.koin.compose.viewmodel.koinViewModel

/*
 * A cloud AI provider gets the user's sentences, and with them things about the user. Nothing is sent to one
 * until the user has been told what goes to whom and has allowed it, once per provider. The permission is
 * asked for where a key is saved (Settings), and by [AiSharingPrompt] for a key that was saved before
 * permission existed. Removing the key withdraws it.
 */

/** The company behind a cloud provider: who receives the text. */
internal val TranslationProvider.company: String
    get() = cloud?.company ?: label

/** Where a cloud provider's privacy policy is published; null for what runs on the phone. */
internal val TranslationProvider.privacyPolicyUrl: String?
    get() = cloud?.privacyPolicyUrl

/** The page of a cloud provider's site where an API key is made; null for what runs on the phone. */
internal val TranslationProvider.keyUrl: String?
    get() = cloud?.keyUrl

/** What Google's terms for a key without billing allow, said wherever a Gemini key is asked for. */
internal const val FREE_GEMINI_KEY_WARNING =
    "With a free Gemini key, Google may use what you send to improve its products, and people there may read it " +
        "(not in the European Economic Area, the UK or Switzerland). Leave out anything you wouldn't want read."

/** What a cloud provider is sent, one line each. */
private val SENT = listOf(
    "The sentences you add, check or ask about, and the words you tap to have them explained.",
    "Your coast's language, goal and region, and the names of its islands.",
    "Cards you already have, so that new sentences fit them and don't repeat them.",
    "Whether sentences about you are worded as a woman, as a man or neutrally.",
    "Your name and job, and where you live and come from, in starter sentences that have a blank for them.",
    "Your About you answers for an island, when you have that island written. They can include family and health details."
)

/**
 * The providers whose prompt was answered with "Not now" since the app was started. It is kept outside the
 * composition, so that turning the phone doesn't bring the prompt back; starting the app again does.
 */
private var promptsPutOff by mutableStateOf(emptySet<TranslationProvider>())

/**
 * Asks for permission to send text to the picked cloud provider when it has a key but was never allowed:
 * a key saved before the app asked. Sits at the root of the app, so it shows on whatever screen is open.
 * "Not now" hides it until the app is started again; Settings keeps offering the same permission.
 */
@Composable
fun AiSharingPrompt(viewModel: AiSharingPromptViewModel = koinViewModel()) {
    val awaiting by viewModel.awaiting.collectAsState()
    val provider = awaiting ?: return
    if (provider in promptsPutOff) return
    AiSharingDialog(
        provider = provider,
        onAllow = { viewModel.allow(provider) },
        onDismiss = { promptsPutOff = promptsPutOff + provider }
    )
}

/**
 * Says what is sent to [provider], who that is and under whose policy, and asks to allow it.
 *
 * @param onAllow The user allowed it; the caller stores that and closes the dialog.
 * @param onDismiss The user chose "Not now" or left the dialog; nothing may be sent.
 */
@Composable
fun AiSharingDialog(provider: TranslationProvider, onAllow: () -> Unit, onDismiss: () -> Unit) =
    SharingDialog(provider, allowLabel = "Allow", onAllow = onAllow, onDismiss = onDismiss)

/** [AiSharingDialog] for the moment a key is saved, where allowing also saves the key. */
@Composable
internal fun AiSharingSaveDialog(provider: TranslationProvider, onAllow: () -> Unit, onDismiss: () -> Unit) =
    SharingDialog(provider, allowLabel = "Allow and save", onAllow = onAllow, onDismiss = onDismiss)

@Composable
private fun SharingDialog(provider: TranslationProvider, allowLabel: String, onAllow: () -> Unit, onDismiss: () -> Unit) {
    val company = provider.company
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send text to ${provider.label}?") },
        text = {
            // The whole text scrolls: it is longer than a small phone's dialog.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "To translate and to write new sentences, Language Coast sends text straight from this phone to $company, " +
                        "under your own API key. The app has no server, and its developer never sees what you send. " +
                        "While the app is open it also has new sentences for Today written without a tap. " +
                        "Each request counts towards your quota or credit with $company."
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("What is sent", style = MaterialTheme.typography.titleSmall)
                SENT.forEach { line ->
                    Row(modifier = Modifier.padding(top = 6.dp)) {
                        Text("•", modifier = Modifier.clearAndSetSemantics {})
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(line)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "$company handles what it receives under its own terms and privacy policy." +
                        if (provider == TranslationProvider.OpenRouter) " It passes your text on to the company whose model answers." else ""
                )
                if (provider == TranslationProvider.Gemini) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(FREE_GEMINI_KEY_WARNING)
                }
                PrivacyPolicyLink(provider)
                Text("You can take this back at any time: remove the key under Settings.")
            }
        },
        confirmButton = {
            TextButton(onClick = onAllow) { Text(allowLabel, color = SunlitText, style = MaterialTheme.typography.labelMedium) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now", color = TextPrimary, style = MaterialTheme.typography.labelMedium) }
        }
    )
}

/**
 * The standing lines under a cloud provider's key in Settings: what [provider] receives and how, so it can
 * be read again after the permission dialog is gone.
 */
@Composable
internal fun AiSharingSummary(provider: TranslationProvider) {
    Hint(
        "${provider.company} receives what you translate, check or ask about. To suggest new sentences it also gets cards you " +
            "already have, with the coast's goal, region and island names, and how sentences about you are worded. Your name, job, " +
            "and where you live and come from are sent in starter sentences that have a blank for them. Your other About you " +
            "answers are sent only when you have an island written. Everything goes straight from this phone, under your own key."
    )
    PrivacyPolicyLink(provider)
}

/** A link to [provider]'s privacy policy. If nothing on the phone can open it, the address is shown instead. */
@Composable
private fun PrivacyPolicyLink(provider: TranslationProvider) {
    val url = provider.privacyPolicyUrl ?: return
    val uriHandler = LocalUriHandler.current
    var failed by remember(url) { mutableStateOf(false) }
    TextButton(
        onClick = { failed = runCatching { uriHandler.openUri(url) }.isFailure },
        colors = ButtonDefaults.textButtonColors(contentColor = SunlitText),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text("${provider.company}'s privacy policy", style = MaterialTheme.typography.labelMedium)
    }
    if (failed) Text(url, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
}
