package com.andreaserick.languagecoast.ui.coast

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.andreaserick.languagecoast.ui.theme.SurfaceSelected
import com.andreaserick.languagecoast.ui.theme.TextPrimary
import com.andreaserick.languagecoast.ui.theme.TextSecondary

/**
 * The last briefs used on this coast, newest first, in one row that scrolls sideways right under the brief
 * box. Tapping one puts that brief back in the box whole, in place of what is there: it was a drill that
 * worked, and mixing it with another would make a different one.
 *
 * They are drawn outlined, with a history mark, and never in the sunlit fill of a picked example chip:
 * bringing back a past brief and adding an example to this one are different things. The brief already
 * in the box is left out: a chip repeating the box only clutters it. Nothing is drawn before the first
 * custom sheet, or when the only recent brief is the one in the box.
 */
@Composable
internal fun RecentBriefChips(briefs: List<String>, current: String, onPick: (String) -> Unit) {
    val others = briefs.filterNot { it.equals(current.trim(), ignoreCase = true) }
    if (others.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).horizontalScroll(rememberScrollState())
    ) {
        others.forEach { brief ->
            AssistChip(
                onClick = { onPick(brief) },
                // A long brief is cut short on the chip; a screen reader still reads it whole.
                label = { Text(brief, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                // The mark also names the row for a screen reader: "Recent brief, ...".
                leadingIcon = { Icon(Icons.Default.History, contentDescription = "Recent brief", modifier = Modifier.size(18.dp)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color.Transparent,
                    labelColor = TextPrimary,
                    leadingIconContentColor = TextSecondary
                ),
                border = BorderStroke(1.dp, SurfaceSelected),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.widthIn(max = 260.dp)
            )
        }
    }
}
