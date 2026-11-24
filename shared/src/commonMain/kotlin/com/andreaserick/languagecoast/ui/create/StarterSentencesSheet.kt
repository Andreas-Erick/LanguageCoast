package com.andreaserick.languagecoast.ui.create

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.andreaserick.languagecoast.data.PhraseBank
import com.andreaserick.languagecoast.data.normalizedWords
import com.andreaserick.languagecoast.data.profileLabel
import com.andreaserick.languagecoast.ui.components.ButtonHeight
import com.andreaserick.languagecoast.ui.components.CoastButton
import com.andreaserick.languagecoast.ui.components.CoastTextField
import com.andreaserick.languagecoast.ui.components.ContentCard
import com.andreaserick.languagecoast.ui.components.coastChipBorder
import com.andreaserick.languagecoast.ui.components.coastChipColors
import com.andreaserick.languagecoast.ui.components.plural
import com.andreaserick.languagecoast.ui.components.rememberGuardedSheetState
import com.andreaserick.languagecoast.ui.theme.CoralText
import com.andreaserick.languagecoast.ui.theme.OnSunlit
import com.andreaserick.languagecoast.ui.theme.Seafoam
import com.andreaserick.languagecoast.ui.theme.SurfaceRaised
import com.andreaserick.languagecoast.ui.theme.Sunlit
import com.andreaserick.languagecoast.ui.theme.SunlitText
import com.andreaserick.languagecoast.ui.theme.TextPrimary
import com.andreaserick.languagecoast.ui.theme.TextSecondary

/** A starter sentence picked in the sheet, with its placeholders filled in and the topic (and its emoji) it comes from. */
data class StarterSentence(val text: String, val category: String, val emoji: String? = null)

/** Keeps the ticks when the phone is turned: saved as the list of ticked sentences. */
private val TickSaver = listSaver<SnapshotStateMap<String, Boolean>, String>(
    save = { ticks -> ticks.filterValues { it }.keys.toList() },
    restore = { sentences -> mutableStateMapOf<String, Boolean>().apply { sentences.forEach { put(it, true) } } }
)

/** Keeps what was typed into the blanks when the phone is turned: saved as keys and values in turn. */
private val FillerSaver = listSaver<SnapshotStateMap<String, String>, String>(
    save = { fillers -> fillers.flatMap { listOf(it.key, it.value) } },
    restore = { flat -> mutableStateMapOf<String, String>().apply { flat.chunked(2).forEach { pair -> if (pair.size == 2) put(pair[0], pair[1]) } } }
)

/**
 * The starter sentences of the [PhraseBank], by topic, to tick and turn into cards on the coast. A ticked
 * sentence with a blank like `[city]` shows a field for it and only counts once the field is filled;
 * `[language]` is filled with [coastLanguage]. Sentences the coast already has are marked and can't be
 * ticked again.
 *
 * @param coastSentences The coast's sentences, normalized as [normalizedWords] joined by spaces.
 * @param profile The user's blanks from Settings, by placeholder; a field starts with that value.
 * @param onAdd Called with the ticked sentences, blanks filled in, when the user adds them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StarterSentencesSheet(
    coastLanguage: String,
    nativeLanguage: String,
    coastSentences: Set<String>,
    profile: Map<String, String>,
    onAdd: (List<StarterSentence>) -> Unit,
    onDismiss: () -> Unit
) {
    var topic by rememberSaveable { mutableStateOf(PhraseBank.THEMES.first().category) }
    // Ticks and fill-ins survive switching topics, so a selection can span several, and turning the phone.
    val ticked = rememberSaveable(saver = TickSaver) { mutableStateMapOf<String, Boolean>() }
    val fillers = rememberSaveable(saver = FillerSaver) { mutableStateMapOf<String, String>() }
    val automatic = mapOf(PhraseBank.LANGUAGE_PLACEHOLDER to coastLanguage)

    // A blank's value: what was typed for this sentence, else the profile's.
    fun valueOf(sentence: String, placeholder: String): String = fillers["$sentence|$placeholder"] ?: profile[placeholder].orEmpty()
    fun filled(sentence: String): String? =
        PhraseBank.fill(sentence, automatic + PhraseBank.placeholders(sentence).associateWith { valueOf(sentence, it) })
    fun isOnCoast(sentence: String): Boolean = normalizedWords(sentence).joinToString(" ") in coastSentences

    val picked = PhraseBank.THEMES.flatMap { theme ->
        theme.sentences.filter { ticked[it] == true }.mapNotNull { sentence -> filled(sentence)?.let { StarterSentence(it, theme.category, theme.emoji) } }
    }
    val tickedCount = ticked.count { it.value }
    var confirmDiscard by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberGuardedSheetState(guarded = tickedCount > 0, onBlocked = { confirmDiscard = true })
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f).imePadding()) {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Text("Starter sentences", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(
                    text = "Sentences everyone needs. Tick what you'd say, fill in the blanks, and they're translated into $coastLanguage and filed on islands.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp)
            ) {
                PhraseBank.THEMES.forEach { theme ->
                    val count = theme.sentences.count { ticked[it] == true }
                    FilterChip(
                        selected = theme.category == topic,
                        onClick = { topic = theme.category },
                        label = {
                            Text(
                                text = if (count == 0) "${theme.emoji} ${theme.category}" else "${theme.emoji} ${theme.category} · $count",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = coastChipColors(),
                        border = coastChipBorder(enabled = true, selected = theme.category == topic),
                        shape = MaterialTheme.shapes.small
                    )
                }
            }

            val theme = PhraseBank.theme(topic) ?: PhraseBank.THEMES.first()
            // Each topic starts at its top, not wherever the last one was scrolled to.
            val listScroll = key(topic) { rememberScrollState() }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(listScroll)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                ContentCard(padding = PaddingValues(vertical = 4.dp, horizontal = 4.dp)) {
                    theme.sentences.forEachIndexed { index, sentence ->
                        if (index > 0) HorizontalDivider(color = SurfaceRaised, modifier = Modifier.padding(horizontal = 12.dp))
                        val onCoast = isOnCoast(sentence)
                        val isTicked = ticked[sentence] == true
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                // The row is the tick box: one stop for a screen reader, which hears the sentence and whether it is ticked.
                                // A sentence the coast has is no tick box at all.
                                .then(
                                    if (onCoast) Modifier
                                    else Modifier.toggleable(value = isTicked, role = Role.Checkbox) { ticked[sentence] = it }
                                )
                                .padding(end = 12.dp)
                        ) {
                            if (onCoast) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Already on the coast",
                                    tint = Seafoam,
                                    modifier = Modifier.padding(horizontal = 14.dp).width(20.dp)
                                )
                            } else {
                                Checkbox(
                                    checked = isTicked,
                                    onCheckedChange = null,
                                    // Without a handler of its own the box is small; the padding keeps the row as tall as before.
                                    modifier = Modifier.padding(12.dp),
                                    colors = CheckboxDefaults.colors(checkedColor = Sunlit, checkmarkColor = OnSunlit, uncheckedColor = TextSecondary)
                                )
                            }
                            Text(
                                text = sentence,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (onCoast) TextSecondary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f).padding(vertical = if (onCoast) 12.dp else 0.dp)
                            )
                        }
                        if (isTicked) {
                            PhraseBank.placeholders(sentence).filterNot { it == PhraseBank.LANGUAGE_PLACEHOLDER }.forEach { placeholder ->
                                val key = "$sentence|$placeholder"
                                CoastTextField(
                                    value = valueOf(sentence, placeholder),
                                    onValueChange = { fillers[key] = it },
                                    label = { Text(if (placeholder in profile) profileLabel(placeholder) else "Fill in: $placeholder") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().padding(start = 48.dp, end = 12.dp, bottom = 8.dp)
                                )
                            }
                        }
                    }
                }
                Text(
                    text = if (nativeLanguage == "English") "You can change the wording later: every card can be edited."
                    else "The sentences are written in English; each card's $nativeLanguage side is translated for you as well.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
                if (tickedCount > picked.size) {
                    Text(
                        text = "${plural(tickedCount - picked.size, "sentence")} still ${if (tickedCount - picked.size == 1) "has" else "have"} a blank to fill in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SunlitText,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                CoastButton(
                    onClick = { onAdd(picked) },
                    enabled = picked.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight)
                ) {
                    Text(if (picked.isEmpty()) "Tick some sentences" else "Add ${plural(picked.size, "card")}")
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard your picks?") },
            text = { Text("${plural(tickedCount, "ticked sentence")} won't be added.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onDismiss()
                }) { Text("Discard", color = CoralText, style = MaterialTheme.typography.labelMedium) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text("Keep picking", color = TextPrimary, style = MaterialTheme.typography.labelMedium)
                }
            }
        )
    }
}
