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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andreaserick.languagecoast.data.PlacedCard
import com.andreaserick.languagecoast.ui.components.CardListRow
import com.andreaserick.languagecoast.ui.components.EditCardSheet
import com.andreaserick.languagecoast.ui.components.LocalUndoMessenger
import com.andreaserick.languagecoast.ui.components.ScreenHeader
import com.andreaserick.languagecoast.ui.components.plural
import com.andreaserick.languagecoast.ui.theme.MistWhite
import com.andreaserick.languagecoast.ui.theme.SandBeige

/**
 * Search across every coast. Results show where each card is; tapping one edits it, like in an island's card list.
 *
 * @param onNavigateBack Called when the back arrow is tapped.
 */
@Composable
fun SearchScreen(onNavigateBack: () -> Unit, viewModel: SearchViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val undoMessenger = LocalUndoMessenger.current
    // Kept here rather than in the ViewModel so typing never waits for the results.
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<PlacedCard?>(null) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        viewModel.onQueryChange(query)
        // Opening search is a request to type, so bring up the keyboard right away.
        if (query.isEmpty()) focusRequester.requestFocus()
    }

    editing?.let { placed ->
        EditCardSheet(
            card = placed.card,
            islands = uiState.islandsByCoast[placed.coastId].orEmpty(),
            nativeLanguage = uiState.nativeLanguage,
            targetLanguage = placed.language,
            onSave = { edit ->
                viewModel.editCard(placed.card.cardId, edit)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Search", onBack = onNavigateBack)

        OutlinedTextField(
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
                .padding(start = 24.dp, end = 24.dp, top = 16.dp)
                .focusRequester(focusRequester)
        )

        val message = when {
            uiState.isLoading -> null
            !uiState.hasCards -> "No cards yet. Create some first!"
            !uiState.hasQuery -> "Find a card on any coast by its native text, translation or alternatives."
            uiState.results.isEmpty() -> "No cards found."
            else -> null
        }
        if (message != null) {
            Text(
                message,
                color = if (uiState.hasQuery) SandBeige else MistWhite,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            )
        } else if (!uiState.isLoading) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "count") {
                    Text(plural(uiState.results.size, "card") + " found", color = MistWhite, modifier = Modifier.padding(top = 8.dp))
                }
                items(uiState.results, key = { it.card.cardId }) { placed ->
                    CardListRow(
                        card = placed.card,
                        now = uiState.now,
                        place = "${placed.language} · ${placed.emoji} ${placed.islandName}",
                        onEdit = { editing = placed },
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
