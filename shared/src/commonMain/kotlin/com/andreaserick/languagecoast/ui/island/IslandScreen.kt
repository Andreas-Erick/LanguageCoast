package com.andreaserick.languagecoast.ui.island

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Waves
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andreaserick.languagecoast.ui.components.CardListRow
import com.andreaserick.languagecoast.ui.components.EditCardSheet
import com.andreaserick.languagecoast.ui.components.EmptyState
import com.andreaserick.languagecoast.ui.components.LocalUndoMessenger
import com.andreaserick.languagecoast.ui.components.ScreenHeader
import com.andreaserick.languagecoast.ui.components.ScreenMargin
import com.andreaserick.languagecoast.ui.components.cardCountLabel
import org.koin.compose.viewmodel.koinViewModel

/**
 * The cards of one "Language Island": each card's native text, translation and when it is due next.
 * Tapping a card (or Edit card in its ⋮ menu) opens [EditCardSheet], where it can also be moved to
 * another island. The island is taken from the navigation arguments by [IslandViewModel].
 *
 * @param onNavigateBack Called when the back arrow is tapped.
 */
@Composable
fun IslandScreen(onNavigateBack: () -> Unit, viewModel: IslandViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val undoMessenger = LocalUndoMessenger.current
    // Kept as the card's id, so the open sheet survives a rotation; the card itself is looked up in the list.
    var editingCardId by rememberSaveable { mutableStateOf<Int?>(null) }

    uiState.cards.firstOrNull { it.cardId == editingCardId }?.let { card ->
        EditCardSheet(
            card = card,
            islands = uiState.coastIslands,
            nativeLanguage = uiState.nativeLanguage,
            targetLanguage = uiState.targetLanguage,
            onSave = { edit ->
                viewModel.editCard(card.cardId, edit)
                editingCardId = null
            },
            onDismiss = { editingCardId = null }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = listOf(uiState.emoji, uiState.islandName).filter { it.isNotEmpty() }.joinToString(" "),
            subtitle = if (uiState.isLoading) null else cardCountLabel(uiState.cards.size, uiState.dueCount),
            onBack = onNavigateBack
        )

        when {
            // Show nothing while loading to avoid flashing the empty state.
            uiState.isLoading -> Unit
            // An island is left empty when its last card is deleted or moved: say how cards get onto it.
            uiState.cards.isEmpty() -> EmptyState(
                icon = Icons.Default.Waves,
                title = "No cards on this island yet",
                body = "Create a card on Today, or move one here from another island when you edit it."
            )
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = ScreenMargin, end = ScreenMargin, top = 12.dp, bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.cards, key = { it.cardId }) { card ->
                    CardListRow(
                        card = card,
                        now = uiState.now,
                        language = uiState.targetLanguage,
                        onEdit = { editingCardId = card.cardId },
                        onDelete = {
                            viewModel.deleteCard(card) { deleted ->
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
