package com.andreaserick.languagecoast.ui.coast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andreaserick.languagecoast.data.IslandSummary
import com.andreaserick.languagecoast.data.islandEmoji
import com.andreaserick.languagecoast.ui.components.LocalUndoMessenger
import com.andreaserick.languagecoast.ui.components.cardCountLabel
import com.andreaserick.languagecoast.ui.components.ScreenHeader
import com.andreaserick.languagecoast.ui.components.lastStudiedLabel
import com.andreaserick.languagecoast.ui.components.plural
import com.andreaserick.languagecoast.ui.theme.DeepOceanBlue
import com.andreaserick.languagecoast.ui.theme.SandBeige
import com.andreaserick.languagecoast.ui.theme.WaveTeal
import com.andreaserick.languagecoast.ui.theme.cardTint
import java.time.LocalDate

/**
 * A single coast: a grid of its "Language Islands" (categories) that contain flashcards.
 *
 * @param onIslandClick Called with the island ID and name when an island is tapped.
 * @param onShowCards Called with the island ID and name when See cards is picked in an island's menu.
 * @param onNavigateBack Called when the back arrow is tapped.
 */
@Composable
fun CoastScreen(
    onIslandClick: (Int, String) -> Unit,
    onShowCards: (Int, String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: CoastViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val undoMessenger = LocalUndoMessenger.current

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = uiState.coastName, subtitle = uiState.nativeName, onBack = onNavigateBack)

        when {
            // Show nothing while loading to avoid flashing the empty state.
            uiState.isLoading -> Unit
            uiState.islands.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("🏝️", fontSize = 56.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "This coast has no islands yet. Go create some flashcards!",
                    color = SandBeige,
                    textAlign = TextAlign.Center
                )
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.islands, key = { it.island.islandId }) { summary ->
                    IslandCard(
                        summary = summary,
                        today = uiState.today,
                        onClick = { onIslandClick(summary.island.islandId, summary.island.name) },
                        onShowCards = { onShowCards(summary.island.islandId, summary.island.name) },
                        onDeleteClick = {
                            viewModel.deleteIsland(summary.island) { deleted ->
                                undoMessenger.show("${summary.island.name} deleted") { viewModel.restore(deleted) }
                            }
                        },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

/**
 * A square card for one "Language Island": its emoji, name, card count and last study session,
 * with a ⋮ menu to see its cards or delete it.
 */
@Composable
fun IslandCard(
    summary: IslandSummary,
    today: LocalDate,
    onClick: () -> Unit,
    onShowCards: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val island = summary.island
    val onCardSecondary = DeepOceanBlue.copy(alpha = 0.85f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = cardTint(island.islandId), contentColor = DeepOceanBlue)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            IslandMenu(
                islandName = island.name,
                tint = onCardSecondary,
                onShowCards = onShowCards,
                onDelete = onDeleteClick,
                modifier = Modifier.align(Alignment.TopEnd)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 12.dp)
            ) {
                Text(text = islandEmoji(island), fontSize = 36.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = island.name,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(text = cardCountLabel(summary.cardCount, summary.dueCount), fontSize = 13.sp, color = onCardSecondary)
            }

            Text(
                text = lastStudiedLabel(island.lastStudied, today),
                fontSize = 11.sp,
                color = onCardSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 10.dp, start = 8.dp, end = 8.dp)
            )
        }
    }
}

/** The ⋮ menu of an island card: See cards and Delete island. */
@Composable
private fun IslandMenu(
    islandName: String,
    tint: Color,
    onShowCards: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        // IconButton keeps a 48dp touch target even though the icon is small.
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Options for $islandName", tint = tint)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = WaveTeal) {
            DropdownMenuItem(
                text = { Text("See cards", color = Color.White) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = null, tint = Color.White) },
                onClick = {
                    expanded = false
                    onShowCards()
                }
            )
            DropdownMenuItem(
                text = { Text("Delete island", color = Color.White) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White) },
                onClick = {
                    expanded = false
                    onDelete()
                }
            )
        }
    }
}
