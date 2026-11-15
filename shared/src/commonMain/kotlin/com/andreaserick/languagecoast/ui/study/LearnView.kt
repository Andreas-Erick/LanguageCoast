package com.andreaserick.languagecoast.ui.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.andreaserick.languagecoast.data.PlacedCard
import com.andreaserick.languagecoast.data.SuggestedSentence
import com.andreaserick.languagecoast.data.emojiForCategory
import com.andreaserick.languagecoast.ui.components.ButtonHeight
import com.andreaserick.languagecoast.ui.components.CoastButton
import com.andreaserick.languagecoast.ui.components.CoastSecondaryButton
import com.andreaserick.languagecoast.ui.components.ContentCard
import com.andreaserick.languagecoast.ui.components.Pill
import com.andreaserick.languagecoast.ui.components.ScreenMargin
import com.andreaserick.languagecoast.ui.components.inLanguage
import com.andreaserick.languagecoast.ui.theme.SurfaceRaised
import com.andreaserick.languagecoast.ui.theme.SunlitText
import com.andreaserick.languagecoast.ui.theme.TextPrimary
import com.andreaserick.languagecoast.ui.theme.TextSecondary

/**
 * The learn step of a daily session for a sentence the app suggests: both sides shown, to keep (it becomes
 * a card, practiced at the end of the session) or skip.
 *
 * @param index Which of the session's [total] new sentences this is, from 1.
 * @param onSpeak Reads the translation aloud; null hides the speaker button.
 * @param targetLanguage The language of the translation, and [nativeLanguage] the user's own (English names).
 */
@Composable
internal fun LearnView(
    sentence: SuggestedSentence,
    index: Int,
    total: Int,
    onKeep: () -> Unit,
    onSkip: () -> Unit,
    onSpeak: (() -> Unit)?,
    targetLanguage: String = "",
    nativeLanguage: String = ""
) = LearnStep(
    heading = "New sentence · $index of $total",
    category = "${sentence.emoji ?: emojiForCategory(sentence.category)} ${sentence.category}",
    targetText = sentence.targetText,
    nativeText = sentence.nativeText,
    hint = "Keep it and it goes on your coast; you practice it at the end of today's session.",
    primary = "Keep it",
    secondary = "Skip",
    onPrimary = onKeep,
    onSecondary = onSkip,
    onSpeak = onSpeak,
    targetLanguage = targetLanguage,
    nativeLanguage = nativeLanguage
)

/**
 * The learn step of a daily session for a card that is on the coast but was never studied: both sides
 * shown, to learn (practiced at the end of the session) or to wave through as known already.
 *
 * @param index Which of the session's [total] new cards this is, from 1.
 */
@Composable
internal fun IntroduceView(
    card: PlacedCard,
    index: Int,
    total: Int,
    onLearn: () -> Unit,
    onKnow: () -> Unit,
    onSpeak: (() -> Unit)?,
    targetLanguage: String = "",
    nativeLanguage: String = ""
) = LearnStep(
    heading = "New card · $index of $total",
    category = "${card.emoji} ${card.islandName}",
    targetText = card.card.targetText,
    nativeText = card.card.nativeText,
    hint = "You practice it at the end of today's session. Know it already? It comes back in a few days instead.",
    primary = "Learn it",
    secondary = "I know this",
    onPrimary = onLearn,
    onSecondary = onKnow,
    onSpeak = onSpeak,
    targetLanguage = targetLanguage,
    nativeLanguage = nativeLanguage
)

/** A new sentence or card with both sides shown and two ways on. */
@Composable
private fun LearnStep(
    heading: String,
    category: String,
    targetText: String,
    nativeText: String,
    hint: String,
    primary: String,
    secondary: String,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    onSpeak: (() -> Unit)?,
    targetLanguage: String,
    nativeLanguage: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = ScreenMargin, end = ScreenMargin, top = 12.dp, bottom = 20.dp)
    ) {
        Text(
            text = heading,
            style = MaterialTheme.typography.labelMedium,
            color = SunlitText,
            modifier = Modifier.asHeading()
        )
        Spacer(modifier = Modifier.height(12.dp))

        // The card sits in the middle of the room that is left. Where there is none (a phone on its side,
        // large text) the two spacers give way and the screen scrolls, instead of the card being squeezed.
        Spacer(modifier = Modifier.weight(1f))
        ContentCard(padding = PaddingValues(horizontal = 20.dp, vertical = 24.dp)) {
            Pill(
                text = category,
                container = SurfaceRaised,
                content = TextSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = inLanguage(targetText, targetLanguage),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                if (onSpeak != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onSpeak) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Read aloud", tint = SunlitText)
                    }
                }
            }
            HorizontalDivider(color = SurfaceRaised, modifier = Modifier.padding(vertical = 16.dp))
            Text(
                text = inLanguage(nativeText, nativeLanguage),
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.weight(1f))

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        // A label that doesn't fit its half (a large font size) wraps instead of losing a word, and both
        // buttons grow to the taller one.
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            CoastSecondaryButton(onClick = onSecondary, modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = ButtonHeight)) {
                Text(secondary, textAlign = TextAlign.Center)
            }
            CoastButton(onClick = onPrimary, modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = ButtonHeight)) {
                Text(primary, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Marks a title as a heading for screen readers. A function of its own, as [LearnStep] has a text called "heading". */
private fun Modifier.asHeading(): Modifier = semantics { heading() }
