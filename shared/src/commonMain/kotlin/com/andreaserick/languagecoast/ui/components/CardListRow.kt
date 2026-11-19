package com.andreaserick.languagecoast.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.andreaserick.languagecoast.data.Flashcard
import com.andreaserick.languagecoast.data.isDue
import com.andreaserick.languagecoast.ui.theme.SunlitText
import com.andreaserick.languagecoast.ui.theme.TextSecondary

/**
 * One card in a list of cards: translation, native text, alternatives and when it is due next,
 * with a ⋮ menu. Tapping the row edits the card.
 *
 * @param now The time the due label is worked out at (epoch millis).
 * @param place Where the card is (e.g. "German · ✈️ Travel"), shown before the due label; null to leave it out.
 * @param language The English name of the language the translation is in (the coast's). With it the translation
 *     is marked as that language, so a screen reader reads it in the right voice; null leaves it unmarked.
 */
@Composable
fun CardListRow(
    card: Flashcard,
    now: Long,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    place: String? = null,
    language: String? = null
) {
    val due = isDue(card, now)
    ContentCard(
        onClick = onEdit,
        onClickLabel = "edit card",
        modifier = modifier,
        padding = PaddingValues(start = 16.dp, top = 12.dp, bottom = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f).padding(top = 4.dp)) {
                if (language != null) {
                    Text(
                        inLanguage(card.targetText, language),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        card.targetText,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    card.nativeText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (card.alternatives.isNotEmpty()) {
                    Text(
                        "also: ${card.alternatives.joinToString(" · ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (place != null) {
                        Text(
                            "$place · ",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    Text(
                        dueLabel(card, now),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (due) SunlitText else TextSecondary,
                        maxLines = 1
                    )
                }
            }
            CardOptionsMenu(onEdit = onEdit, onDelete = onDelete, description = "Options for ${card.nativeText}")
        }
    }
}
