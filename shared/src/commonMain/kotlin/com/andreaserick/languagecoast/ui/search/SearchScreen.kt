package com.andreaserick.languagecoast.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andreaserick.languagecoast.ui.components.CardListRow
import com.andreaserick.languagecoast.ui.components.CoastTextField
import com.andreaserick.languagecoast.ui.components.EditCardSheet
import com.andreaserick.languagecoast.ui.components.LocalUndoMessenger
import com.andreaserick.languagecoast.ui.components.ScreenHeader
import com.andreaserick.languagecoast.ui.components.ScreenMargin
import com.andreaserick.languagecoast.ui.components.plural
import com.andreaserick.languagecoast.ui.theme.TextSecondary
import org.koin.compose.viewmodel.koinViewModel

/**
 * Search across every coast. Results show where each card is; tapping one edits it, like in an island's card list.
 *
 * @param onNavigateBack Called when the back arrow is tapped.
 */
@Composable
fun SearchScreen(onNavigateBack: () -> Unit, viewModel: SearchViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val undoMessenger = LocalUndoMessenger.current
    // Kept here rather than in the ViewModel so typing never waits for the results.
    var query by rememberSaveable { mutableStateOf("") }
    // Kept as the card's id, so the open sheet survives a rotation; the card itself is looked up in the results.
    var editingCardId by rememberSaveable { mutableStateOf<Int?>(null) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        viewModel.onQueryChange(query)
        // Opening search is a request to type, so bring up the keyboard right away.
        if (query.isEmpty()) focusRequester.requestFocus()
    }

    // The results lag the field, so a card tapped just before they change can drop out of them. Forget it then,
    // or its sheet would open by itself the next time the card is found.
    LaunchedEffect(uiState.results, uiState.hasQuery) {
        if (editingCardId != null && !uiState.isLoading && uiState.hasQuery && uiState.results.none { it.card.cardId == editingCardId }) editingCardId = null
    }

    uiState.results.firstOrNull { it.card.cardId == editingCardId }?.let { placed ->
        EditCardSheet(
            card = placed.card,
            islands = uiState.islandsByCoast[placed.coastId].orEmpty(),
            nativeLanguage = uiState.nativeLanguage,
            targetLanguage = placed.language,
            onSave = { edit ->
                viewModel.editCard(placed.card.cardId, edit)
                editingCardId = null
            },
            onDismiss = { editingCardId = null }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Search", onBack = onNavigateBack)

        CoastTextField(
            value = query,
            onValueChange = {
                query = it
                viewModel.onQueryChange(it)
            },
            placeholder = { Text("Word or sentence, in any language") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = {
                        query = ""
                        viewModel.onQueryChange("")
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            } else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            // Results update while typing, so the search key only makes room to see them.
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ScreenMargin, end = ScreenMargin, top = 12.dp)
                .focusRequester(focusRequester)
        )

        val message = when {
            uiState.isLoading -> null
            !uiState.hasCards -> "No cards yet. Add some on Today, and you can find them here."
            !uiState.hasQuery -> "Find a card on any coast by its native text, translation or alternatives."
            uiState.results.isEmpty() -> "No cards found."
            else -> null
        }
        // What a search gave arrives while the keyboard has the focus: a screen reader reads it out.
        val announced = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        if (message != null) {
            Text(
                message,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .then(announced)
            )
        } else if (!uiState.isLoading) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = ScreenMargin, end = ScreenMargin, top = 8.dp, bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "count") {
                    Text(plural(uiState.results.size, "card") + " found", style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.padding(top = 8.dp).then(announced))
                }
                items(uiState.results, key = { it.card.cardId }) { placed ->
                    CardListRow(
                        card = placed.card,
                        now = uiState.now,
                        place = "${placed.language} · ${placed.emoji} ${placed.islandName}",
                        language = placed.language,
                        onEdit = { editingCardId = placed.card.cardId },
                        onDelete = {
                            viewModel.deleteCard(placed.card) { deleted ->
                                undoMessenger.show("Card deleted") { viewModel.restore(deleted) }
                            }
                        },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}
