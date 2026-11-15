package com.andreaserick.languagecoast.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.andreaserick.languagecoast.data.Language
import com.andreaserick.languagecoast.data.onDeviceLanguage
import com.andreaserick.languagecoast.data.onDeviceTranslationAvailable
import com.andreaserick.languagecoast.data.search
import com.andreaserick.languagecoast.ui.theme.SunlitText
import com.andreaserick.languagecoast.ui.theme.SurfaceCard
import com.andreaserick.languagecoast.ui.theme.SurfaceRaised
import com.andreaserick.languagecoast.ui.theme.TextPrimary
import com.andreaserick.languagecoast.ui.theme.TextSecondary

/**
 * A dialog for picking one language from a long list, with a search field that matches
 * English and native names (e.g. "Deutsch" finds German).
 *
 * @param title Dialog title.
 * @param languages The languages to choose from.
 * @param onPick Called with the chosen language; the caller is expected to close the dialog.
 * @param subtitle Optional explanation shown under the title.
 * @param selected The currently selected language, highlighted in the list.
 */
@Composable
fun LanguagePickerDialog(
    title: String,
    languages: List<Language>,
    onPick: (Language) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    selected: String? = null
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(languages, query) { languages.search(query) }
    // Open scrolled to the current selection, with the row above it still visible as context.
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (languages.indexOfFirst { it.name == selected } - 1).coerceAtLeast(0)
    )

    Dialog(onDismissRequest = onDismiss) {
        // The same surface as the app's other dialogs. The text colour is set here too: it can't be worked
        // out from a colour that isn't one of the theme's containers.
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = SurfaceCard,
            contentColor = TextPrimary,
        ) {
            Column(modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp).semantics { heading() }
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp)
                    )
                }

                CoastTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search languages") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        // The placeholder goes once something is typed; this keeps the field's name for screen readers.
                        .semantics { contentDescription = "Search languages" }
                )

                HorizontalDivider(color = SurfaceRaised)

                // The list takes what is left after the title, the search field and Cancel, so in a short window
                // (landscape, or with the keyboard open) it is the list that shrinks and Cancel stays on screen.
                Box(modifier = Modifier.weight(1f, fill = false).heightIn(min = 120.dp, max = 360.dp)) {
                    if (results.isEmpty()) {
                        Text(
                            text = if (languages.isEmpty()) "No languages left to pick." else "No languages match \"$query\".",
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp)
                        )
                    } else {
                        LazyColumn(state = listState) {
                            items(results, key = { it.code }) { language ->
                                LanguageRow(
                                    language = language,
                                    isSelected = language.name == selected,
                                    onClick = { onPick(language) }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = SurfaceRaised)

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(end = 12.dp, top = 4.dp)
                ) {
                    Text("Cancel", color = TextPrimary, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/**
 * A read-only field showing [language] that opens a [LanguagePickerDialog] when tapped.
 *
 * @param language The selected language's name.
 * @param languages The languages offered in the picker.
 * @param onPick Called with the newly chosen language.
 * @param pickerTitle Title of the picker dialog.
 * @param pickerSubtitle Optional explanation shown under the picker's title.
 * @param label What the field sets (e.g. "Native language"), read by screen readers before the language.
 */
@Composable
fun LanguageField(
    language: String,
    languages: List<Language>,
    onPick: (Language) -> Unit,
    pickerTitle: String,
    modifier: Modifier = Modifier,
    pickerSubtitle: String? = null,
    label: String? = null
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    // A read-only text field still consumes taps, so open the picker from its press interactions.
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { if (it is PressInteraction.Release) showPicker = true }
    }

    CoastTextField(
        value = language,
        onValueChange = {},
        readOnly = true,
        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            // A screen reader, a switch or a keyboard doesn't press: its click on a read-only field only takes
            // focus. So the field is one stop ("Native language: English, drop-down list") whose click opens the picker.
            .clearAndSetSemantics {
                role = Role.DropdownList
                contentDescription = if (label != null) "$label: $language" else language
                onClick(label = "Choose language") {
                    showPicker = true
                    true
                }
            }
    )

    if (showPicker) {
        LanguagePickerDialog(
            title = pickerTitle,
            subtitle = pickerSubtitle,
            languages = languages,
            selected = language,
            onPick = {
                onPick(it)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

/**
 * One language in the list: its English name, its native name and, where the phone can't translate it by
 * itself, that it needs an AI provider. Checked if selected.
 */
@Composable
private fun LanguageRow(language: Language, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = language.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = TextPrimary
            )
            val nativeName = language.nativeName.takeIf { it != language.name }
            // Where the phone translates by itself, a language it doesn't cover only works with an AI provider.
            // Better to say so here than to let the first translation fail.
            val needsProvider = remember(language.name) { onDeviceTranslationAvailable && onDeviceLanguage(language.name) == null }
            if (nativeName != null || needsProvider) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildAnnotatedString {
                        // Tagged as that language, so it is read in its voice and drawn in its letter shapes.
                        if (nativeName != null) append(inLanguage(nativeName, language.name))
                        if (nativeName != null && needsProvider) append(" · ")
                        if (needsProvider) append("Needs an AI provider")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
        if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = "Selected", tint = SunlitText, modifier = Modifier.size(20.dp))
        }
    }
}
