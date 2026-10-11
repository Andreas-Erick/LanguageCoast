package com.andreaserick.languagecoast.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andreaserick.languagecoast.data.Flashcard
import com.andreaserick.languagecoast.data.isDue
import com.andreaserick.languagecoast.ui.theme.CardWash
import com.andreaserick.languagecoast.ui.theme.MistWhite
import com.andreaserick.languagecoast.ui.theme.SandBeige

/**
 * One card in a list of cards: native text, translation, alternatives and when it is due next,
 * with a ⋮ menu. Tapping the row edits the card.
 *
 * @param now The time the due label is worked out at (epoch millis).
 * @param place Where the card is (e.g. "German · ✈️ Travel"), shown before the due label; null to leave it out.
 */
@Composable
fun CardListRow(
    card: Flashcard,
    now: Long,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    place: String? = null
) {
    val due = isDue(card, now)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Edit card", onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = CardWash, contentColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp)) {
            Column(modifier = Modifier.weight(1f).padding(top = 4.dp)) {
                Text(card.nativeText, color = MistWhite, fontSize = 14.sp)
                Text(card.targetText, color = SandBeige, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                if (card.alternatives.isNotEmpty()) {
                    Text(
                        "also: ${card.alternatives.joinToString(" · ")}",
                        color = MistWhite,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row {
                    if (place != null) {
                        Text("$place · ", color = MistWhite, fontSize = 12.sp, maxLines = 1)
                    }
                    Text(
                        dueLabel(card, now),
                        color = if (due) SandBeige else MistWhite,
                        fontSize = 12.sp,
                        fontWeight = if (due) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
            CardOptionsMenu(onEdit = onEdit, onDelete = onDelete, description = "Options for ${card.nativeText}")
        }
    }
}
