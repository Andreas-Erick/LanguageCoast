package com.andreaserick.languagecoast.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.andreaserick.languagecoast.ui.components.collapseExit
import com.andreaserick.languagecoast.ui.components.expandEnter
import com.andreaserick.languagecoast.ui.theme.TextPrimary
import com.andreaserick.languagecoast.ui.theme.TextSecondary

/**
 * A row in a settings card that hides details most people never need (which model, what the phone is made
 * of) until it is tapped. Keeping them folded away leaves the card with only what a newcomer has to decide.
 *
 * @param subtitle What is set now, so the folded row still says it without being opened; null for none.
 * @param expanded Whether [content] shows. The caller keeps it, so it can survive a turn of the phone.
 */
@Composable
internal fun CollapsibleRow(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClickLabel = if (expanded) "Hide" else "Show", role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = if (expanded) "Open" else "Closed" }
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = TextSecondary
        )
    }
    AnimatedVisibility(visible = expanded, enter = expandEnter(), exit = collapseExit()) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), content = content)
    }
}
