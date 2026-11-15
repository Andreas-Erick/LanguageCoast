package com.andreaserick.languagecoast.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.andreaserick.languagecoast.data.Pace
import com.andreaserick.languagecoast.data.daysToLearn
import com.andreaserick.languagecoast.ui.theme.SurfaceRaised
import com.andreaserick.languagecoast.ui.theme.Sunlit
import com.andreaserick.languagecoast.ui.theme.TextPrimary
import com.andreaserick.languagecoast.ui.theme.TextSecondary

/** "about 3 weeks", "about 2 months": [days] as a length of time one can picture. */
fun roughDuration(days: Int): String = when {
    days <= 1 -> "a day"
    days < 14 -> "$days days"
    days < 60 -> "about ${plural((days + 3) / 7, "week")}"
    else -> "about ${plural((days + 15) / 30, "month")}"
}

/**
 * The three paces to pick from, each with what it means per day and, when [waiting] new cards are on the
 * coast, how long those take at that pace.
 */
@Composable
fun PaceOptions(pace: Pace, waiting: Int, onPick: (Pace) -> Unit, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth().selectableGroup()) {
        Pace.entries.forEach { option ->
            val selected = option == pace
            val shape = MaterialTheme.shapes.medium
            Surface(
                color = SurfaceRaised,
                contentColor = TextPrimary,
                shape = shape,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (selected) Modifier.border(BorderStroke(1.5.dp, Sunlit), shape) else Modifier)
                    .clip(shape)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onPick(option) })
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(option.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${option.newPerDay} new a day · up to ${option.reviewCap} reviews" +
                                if (waiting > 0) " · ${roughDuration(daysToLearn(waiting, option))} for what's waiting" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = Sunlit, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/**
 * Picks how fast to go, from Today: the three paces with what each means for the [waiting] new cards on
 * the coast. A pick is stored at once; the sheet stays open so the choice can be seen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaceSheet(pace: Pace, waiting: Int, onPick: (Pace) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            Text("How fast do you want to go?", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "The pace sets how many new cards a day's session brings. Everything new comes back for review, so a faster pace means longer sessions for weeks, not just today.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            PaceOptions(pace = pace, waiting = waiting, onPick = onPick)
            Text(
                text = "Miss a few days and the session shrinks by itself; nothing piles up at any pace.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            CoastButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}
