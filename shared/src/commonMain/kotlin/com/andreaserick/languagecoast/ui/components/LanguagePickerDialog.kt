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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.andreaserick.languagecoast.data.Language
import com.andreaserick.languagecoast.data.search
import com.andreaserick.languagecoast.ui.theme.DeepOceanBlue
import com.andreaserick.languagecoast.ui.theme.SandBeige

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
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = DeepOceanBlue,
            contentColor = Color.White
        ) {
            Column(modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)) {
                Text(
                    text = title,
                    color = SandBeige,
                    fontSize = 24.sp,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp)
                    )
                }

                OutlinedTextField(
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
                )

                HorizontalDivider(color = SandBeige.copy(alpha = 0.2f))

                Box(modifier = Modifier.heightIn(min = 120.dp, max = 360.dp)) {
                    if (results.isEmpty()) {
                        Text(
                            text = if (languages.isEmpty()) "No languages left to pick." else "No languages match \"$query\".",
                            color = Color.White.copy(alpha = 0.7f),
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

                HorizontalDivider(color = SandBeige.copy(alpha = 0.2f))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(end = 12.dp, top = 4.dp)
                ) {
                    Text("Cancel", color = SandBeige)
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
 */
@Composable
fun LanguageField(
    language: String,
    languages: List<Language>,
    onPick: (Language) -> Unit,
    pickerTitle: String,
    modifier: Modifier = Modifier,
    pickerSubtitle: String? = null
) {
    var showPicker by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    // A read-only text field still consumes taps, so open the picker from its press interactions.
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { if (it is PressInteraction.Release) showPicker = true }
    }

    OutlinedTextField(
        value = language,
        onValueChange = {},
        readOnly = true,
        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose language") },
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth()
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

/** One language in the list: its English name, its native name and whether dict.cc covers it, checked if selected. */
@Composable
private fun LanguageRow(language: Language, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = language.name,
                fontSize = 17.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) SandBeige else Color.White
            )
            val details = listOfNotNull(
                language.nativeName.takeIf { it != language.name },
                "No dict.cc lookup".takeIf { !language.onDictCc }
            )
            if (details.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = details.joinToString(" · "),
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
        if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = "Selected", tint = SandBeige, modifier = Modifier.size(20.dp))
        }
    }
}
