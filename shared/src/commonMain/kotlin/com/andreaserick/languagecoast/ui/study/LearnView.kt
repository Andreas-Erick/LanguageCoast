package com.andreaserick.languagecoast.ui.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.andreaserick.languagecoast.data.SuggestedSentence
import com.andreaserick.languagecoast.data.emojiForCategory
import com.andreaserick.languagecoast.ui.components.ButtonHeight
import com.andreaserick.languagecoast.ui.components.CoastButton
import com.andreaserick.languagecoast.ui.components.CoastSecondaryButton
import com.andreaserick.languagecoast.ui.components.ContentCard
import com.andreaserick.languagecoast.ui.components.Pill
import com.andreaserick.languagecoast.ui.components.ScreenMargin
import com.andreaserick.languagecoast.ui.theme.SurfaceRaised
import com.andreaserick.languagecoast.ui.theme.Sunlit
import com.andreaserick.languagecoast.ui.theme.TextPrimary
import com.andreaserick.languagecoast.ui.theme.TextSecondary

/**
 * The learn step of a daily session: a new sentence with both sides shown, to keep (it becomes a card,
 * practiced at the end of the session) or skip.
 *
 * @param index Which of the session's [total] new sentences this is, from 1.
 * @param onSpeak Reads the translation aloud; null hides the speaker button.
 */
@Composable
internal fun LearnView(
    sentence: SuggestedSentence,
    index: Int,
    total: Int,
    onKeep: () -> Unit,
    onSkip: () -> Unit,
    onSpeak: (() -> Unit)?
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = ScreenMargin, end = ScreenMargin, top = 12.dp, bottom = 20.dp)
    ) {
        Text(
            text = "New sentence · $index of $total",
            style = MaterialTheme.typography.labelMedium,
            color = Sunlit
        )
        Spacer(modifier = Modifier.height(12.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            ContentCard(padding = PaddingValues(horizontal = 20.dp, vertical = 24.dp)) {
                Pill(
                    text = "${sentence.emoji ?: emojiForCategory(sentence.category)} ${sentence.category}",
                    container = SurfaceRaised,
                    content = TextSecondary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = sentence.targetText,
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    if (onSpeak != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = onSpeak) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Read aloud", tint = Sunlit)
                        }
                    }
                }
                HorizontalDivider(color = SurfaceRaised, modifier = Modifier.padding(vertical = 16.dp))
                Text(
                    text = sentence.nativeText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Keep it and it goes on your coast; you practice it at the end of today's session.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            CoastSecondaryButton(onClick = onSkip, modifier = Modifier.weight(1f).heightIn(min = ButtonHeight)) {
                Text("Skip")
            }
            CoastButton(onClick = onKeep, modifier = Modifier.weight(2f).heightIn(min = ButtonHeight)) {
                Text("Keep it")
            }
        }
    }
}
