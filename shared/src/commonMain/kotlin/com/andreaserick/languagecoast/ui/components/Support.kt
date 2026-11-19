package com.andreaserick.languagecoast.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.andreaserick.languagecoast.ui.theme.TextSecondary

/** Where the app sends people for help, feedback and its policy. */
object Support {
    /** The address feedback is mailed to. While it is blank, feedback opens [ISSUES_URL] instead. */
    const val EMAIL = ""

    /** The public issue tracker. Nothing the user typed or the AI wrote may be put into a link to it. */
    const val ISSUES_URL = "https://github.com/Andreas-Erick/LanguageCoast/issues"

    const val PRIVACY_POLICY_URL = "https://github.com/Andreas-Erick/LanguageCoast/blob/main/docs/privacy-policy.md"

    const val SOURCE_URL = "https://github.com/Andreas-Erick/LanguageCoast"
}

/**
 * A function that starts a message to the developer. With [Support.EMAIL] set it opens the mail app with
 * the subject and body filled in. Without it, or on an Android phone with no mail app, it opens the issue
 * tracker with nothing attached: that page is public, so card text and AI answers must not travel in its
 * address. An iPhone does not report whether a mail app took the message, so there the tracker opens only
 * while the address is blank.
 *
 * The function returns false when nothing on the phone could open either, so the caller can say so.
 */
@Composable
fun rememberSendFeedback(): (subject: String, body: String) -> Boolean {
    val uriHandler = LocalUriHandler.current
    return remember<(String, String) -> Boolean>(uriHandler) {
        { subject, body ->
            val mailed = Support.EMAIL.isNotBlank() &&
                runCatching { uriHandler.openUri(mailtoUri(Support.EMAIL, subject, body)) }.isSuccess
            mailed || runCatching { uriHandler.openUri(Support.ISSUES_URL) }.isSuccess
        }
    }
}

/**
 * A quiet link under something the AI wrote, for telling the developer it is wrong or offensive.
 *
 * @param details What to put into the message (the card and the answer); only read when the link is tapped.
 */
@Composable
fun ReportAnswerLink(details: () -> String, modifier: Modifier = Modifier) {
    val sendFeedback = rememberSendFeedback()
    // On a phone with nothing to open the link, the address is shown instead: a message at the bottom of the
    // screen would sit behind the sheet this link is in.
    var unopened by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = { unopened = !sendFeedback("Report an AI answer", details()) })
        ) {
            Text(
                text = "Report this answer",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                textDecoration = TextDecoration.Underline
            )
        }
        if (unopened) Text(Support.ISSUES_URL, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

/** A mailto link to [email] with [subject] and [body] filled in. */
internal fun mailtoUri(email: String, subject: String, body: String): String {
    // Mail apps expect line breaks in a mailto body as CR LF.
    val lines = body.replace("\r\n", "\n").replace("\n", "\r\n")
    return "mailto:$email?subject=${percentEncode(subject)}&body=${percentEncode(lines)}"
}

private const val HEX_DIGITS = "0123456789ABCDEF"

/**
 * [text] with every byte of its UTF-8 form written as %XX, except letters, digits and - . _ ~
 * (so a space becomes %20, never +, which mail apps would show as a plus sign).
 */
internal fun percentEncode(text: String): String = buildString {
    text.encodeToByteArray().forEach { byte ->
        val value = byte.toInt() and 0xFF
        val char = value.toChar()
        if (char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in "-._~") {
            append(char)
        } else {
            append('%')
            append(HEX_DIGITS[value shr 4])
            append(HEX_DIGITS[value and 0x0F])
        }
    }
}
