package com.andreaserick.languagecoast.ui.island

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andreaserick.languagecoast.data.Flashcard
import com.andreaserick.languagecoast.data.isDue
import com.andreaserick.languagecoast.ui.components.CardOptionsMenu
import com.andreaserick.languagecoast.ui.components.EditCardSheet
import com.andreaserick.languagecoast.ui.components.LocalUndoMessenger
import com.andreaserick.languagecoast.ui.components.ScreenHeader
import com.andreaserick.languagecoast.ui.components.cardCountLabel
import com.andreaserick.languagecoast.ui.components.dueLabel
import com.andreaserick.languagecoast.ui.theme.CardWash
import com.andreaserick.languagecoast.ui.theme.MistWhite
import com.andreaserick.languagecoast.ui.theme.SandBeige

/**
 * The cards of one "Language Island": each card's native text, translation and when it is due next.
 * Tapping a card (or Edit card in its ⋮ menu) opens [EditCardSheet], where it can also be moved to
 * another island. The island is taken from the navigation arguments by [IslandViewModel].
 *
 * @param onNavigateBack Called when the back arrow is tapped.
 */
@Composable
fun IslandScreen(onNavigateBack: () -> Unit, viewModel: IslandViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val undoMessenger = LocalUndoMessenger.current
    var editingCard by remember { mutableStateOf<Flashcard?>(null) }

    editingCard?.let { card ->
        EditCardSheet(
            card = card,
            islands = uiState.coastIslands,
            nativeLanguage = uiState.nativeLanguage,
            targetLanguage = uiState.targetLanguage,
            onSave = { edit ->
                viewModel.editCard(card.cardId, edit)
                editingCard = null
            },
            onDismiss = { editingCard = null }
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
            uiState.cards.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("🌊", fontSize = 56.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text("No flashcards on this island.", color = SandBeige, textAlign = TextAlign.Center)
            }
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.cards, key = { it.cardId }) { card ->
                    CardRow(
                        card = card,
                        now = uiState.now,
                        onEdit = { editingCard = card },
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

/** One card in the island list: native text, translation, alternatives and when it is due next. */
@Composable
private fun CardRow(card: Flashcard, now: Long, onEdit: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Edit card", onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = CardWash, contentColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp)) {
            Column(modifier = Modifier.weight(1f).padding(top = 4.dp)) {
                Text(card.nativeText, color = MistWhite, fontSize = 14.sp)
                Text(card.targetText, color = SandBeige, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                if (card.alternatives.isNotEmpty()) {
                    Text(
                        "also: ${card.alternatives.joinToString(" · ")}",
                        color = MistWhite,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    dueLabel(card, now),
                    color = if (isDue(card, now)) SandBeige else MistWhite,
                    fontSize = 12.sp,
                    fontWeight = if (isDue(card, now)) FontWeight.SemiBold else FontWeight.Normal
                )
            }
            CardOptionsMenu(onEdit = onEdit, onDelete = onDelete, description = "Options for ${card.nativeText}")
        }
    }
}
