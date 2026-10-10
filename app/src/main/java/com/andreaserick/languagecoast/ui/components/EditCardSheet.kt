package com.andreaserick.languagecoast.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andreaserick.languagecoast.data.CardEdit
import com.andreaserick.languagecoast.data.Flashcard
import com.andreaserick.languagecoast.data.LanguageIsland
import com.andreaserick.languagecoast.data.islandEmoji
import com.andreaserick.languagecoast.ui.theme.DeepOceanBlue
import com.andreaserick.languagecoast.ui.theme.MistWhite
import com.andreaserick.languagecoast.ui.theme.SandBeige

/**
 * A bottom sheet to fix a card: its native text, translation, alternatives (one per line) and the
 * island it is on. Review progress is kept.
 *
 * @param islands The islands of the card's coast, which the card can be moved to.
 * @param targetLanguage The coast's language, for the translation's label.
 * @param onSave Called with the changes when Save is tapped; the sheet should then be closed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCardSheet(
    card: Flashcard,
    islands: List<LanguageIsland>,
    nativeLanguage: String,
    targetLanguage: String,
    onSave: (CardEdit) -> Unit,
    onDismiss: () -> Unit
) {
    var nativeText by rememberSaveable { mutableStateOf(card.nativeText) }
    var targetText by rememberSaveable { mutableStateOf(card.targetText) }
    var alternatives by rememberSaveable { mutableStateOf(card.alternatives.joinToString("\n")) }
    var islandId by rememberSaveable { mutableStateOf(card.islandId) }
    val edit = remember(nativeText, targetText, alternatives, islandId) {
        cardEditOf(card, nativeText, targetText, alternatives, islandId)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = DeepOceanBlue,
        contentColor = Color.White
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            Text("Edit card", style = MaterialTheme.typography.titleLarge, color = SandBeige)
            OutlinedTextField(
                value = nativeText,
                onValueChange = { nativeText = it },
                label = { Text(nativeLanguage) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = targetText,
                onValueChange = { targetText = it },
                label = { Text("$targetLanguage Translation") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = alternatives,
                onValueChange = { alternatives = it },
                label = { Text("Alternatives (Optional)") },
                supportingText = { Text("Other correct translations, one per line", color = MistWhite) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            // Only other islands of the same coast: moving a card to another language makes no sense.
            if (islands.size > 1) {
                SelectionDropdown(
                    options = islands,
                    selected = islands.firstOrNull { it.islandId == islandId },
                    onSelected = { islandId = it.islandId },
                    optionLabel = { "${islandEmoji(it)} ${it.name}" },
                    label = "Island",
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = SandBeige)) {
                    Text("Cancel", fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { edit?.let(onSave) }, enabled = edit != null) {
                    Text("Save", fontSize = 16.sp)
                }
            }
        }
    }
}

/**
 * The [CardEdit] for what was typed into [EditCardSheet], or null if the native text or translation
 * is blank. [alternatives] has one per line; blank lines, repeats and the translation itself are dropped.
 * The card's note describes its alternatives, so it is dropped when none are left.
 */
internal fun cardEditOf(card: Flashcard, nativeText: String, targetText: String, alternatives: String, islandId: Int): CardEdit? {
    val native = nativeText.trim()
    val target = targetText.trim()
    if (native.isEmpty() || target.isEmpty()) return null
    val others = alternatives.lines().map { it.trim() }.filter { it.isNotEmpty() && it != target }.distinct()
    return CardEdit(
        nativeText = native,
        targetText = target,
        alternatives = others,
        note = card.note.takeIf { others.isNotEmpty() },
        islandId = islandId
    )
}
