package com.andreaserick.languagecoast.ui.coast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.andreaserick.languagecoast.ui.components.coastChipBorder
import com.andreaserick.languagecoast.ui.components.coastChipColors
import com.andreaserick.languagecoast.ui.theme.TextSecondary

/**
 * The last briefs used on this coast, newest first, as chips under the brief box and ahead of the example
 * chips. Tapping one puts that brief back in the box whole, in place of what is there: it was a drill that
 * worked, and mixing it with another would make a different one. The brief in the box shows as picked.
 * Nothing is drawn before the first custom sheet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecentBriefChips(briefs: List<String>, current: String, onPick: (String) -> Unit) {
    if (briefs.isEmpty()) return
    Text(
        text = "Recent",
        style = MaterialTheme.typography.labelSmall,
        color = TextSecondary,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        briefs.forEach { brief ->
            val selected = brief.equals(current.trim(), ignoreCase = true)
            FilterChip(
                selected = selected,
                onClick = { onPick(brief) },
                // A long brief is cut short on the chip; a screen reader still reads it whole.
                label = { Text(brief, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = coastChipColors(),
                border = coastChipBorder(enabled = true, selected = selected),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.widthIn(max = 280.dp)
            )
        }
    }
}
