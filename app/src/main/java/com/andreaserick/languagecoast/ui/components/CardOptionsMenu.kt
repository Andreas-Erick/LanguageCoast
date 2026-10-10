package com.andreaserick.languagecoast.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.andreaserick.languagecoast.ui.theme.SandBeige
import com.andreaserick.languagecoast.ui.theme.WaveTeal

/**
 * The ⋮ menu for a card, with Edit card and Delete card.
 *
 * @param description What the menu is for, read by screen readers (e.g. "Options for Hola").
 * @param tint The colour of the ⋮ icon.
 */
@Composable
fun CardOptionsMenu(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    description: String = "Card options",
    tint: Color = SandBeige
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = description, tint = tint)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = WaveTeal) {
            DropdownMenuItem(
                text = { Text("Edit card", color = Color.White) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White) },
                onClick = {
                    expanded = false
                    onEdit()
                }
            )
            DropdownMenuItem(
                text = { Text("Delete card", color = Color.White) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White) },
                onClick = {
                    expanded = false
                    onDelete()
                }
            )
        }
    }
}
