package com.andreaserick.languagecoast.ui.coast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andreaserick.languagecoast.data.IslandSummary
import com.andreaserick.languagecoast.data.PhraseBank
import com.andreaserick.languagecoast.data.islandEmoji
import com.andreaserick.languagecoast.ui.components.ButtonHeight
import com.andreaserick.languagecoast.ui.components.CoastButton
import com.andreaserick.languagecoast.ui.components.ContentCard
import com.andreaserick.languagecoast.ui.components.EmptyState
import com.andreaserick.languagecoast.ui.components.IconTile
import com.andreaserick.languagecoast.ui.components.LocalUndoMessenger
import com.andreaserick.languagecoast.ui.components.ScreenHeader
import com.andreaserick.languagecoast.ui.components.ScreenMargin
import com.andreaserick.languagecoast.ui.components.lastStudiedLabel
import com.andreaserick.languagecoast.ui.components.plural
import com.andreaserick.languagecoast.ui.theme.Sunlit
import com.andreaserick.languagecoast.ui.theme.TextSecondary
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel

/**
 * A single coast: a button to review its busiest island, and a grid of its "Language Islands"
 * (categories) that contain flashcards.
 *
 * @param onIslandClick Called with the island ID and name when an island is tapped.
 * @param onReviewCoast Called with the coast ID and name to study every due card on the coast.
 * @param onShowCards Called with the island ID and name when See cards is picked in an island's menu.
 * @param onNavigateBack Called when the back arrow is tapped.
 */
@Composable
fun CoastScreen(
    onIslandClick: (Int, String) -> Unit,
    onReviewCoast: (Int, String) -> Unit,
    onShowCards: (Int, String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: CoastViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val undoMessenger = LocalUndoMessenger.current
    var topicSheet by remember { mutableStateOf<List<StarterTopicOption>?>(null) }

    topicSheet?.let { options ->
        StarterTopicSheet(
            options = options,
            coastLanguage = uiState.coastLanguage,
            provider = uiState.translationProvider,
            profile = uiState.profile,
            onAdd = { fillers ->
                viewModel.addStarterTopics(options.map { it.theme.category }, fillers)
                topicSheet = null
            },
            onDismiss = { topicSheet = null }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = uiState.coastName, subtitle = uiState.nativeName, onBack = onNavigateBack)

        // Above the grid: a lazy grid keeps its first visible item in place, so a card inserted at its top would land out of view.
        uiState.import?.let { progress ->
            ImportProgressCard(
                progress = progress,
                onDismiss = viewModel::dismissImport,
                modifier = Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = 12.dp)
            )
        }

        when {
            // Show nothing while loading to avoid flashing the empty state.
            uiState.isLoading -> Unit
            uiState.islands.isEmpty() && uiState.starterTopics.isEmpty() -> EmptyState(
                icon = Icons.Default.BeachAccess,
                title = "No islands yet",
                body = "Create a flashcard and its island appears here."
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = ScreenMargin, end = ScreenMargin, top = 12.dp, bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (uiState.starterTopics.isNotEmpty()) {
                    item(key = "starters", span = { GridItemSpan(maxLineSpan) }) {
                        StarterTopicsRow(
                            options = uiState.starterTopics,
                            onPick = { topicSheet = listOf(it) },
                            onAddAll = { topicSheet = uiState.starterTopics },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
                if (uiState.islands.isEmpty()) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "No islands yet. Add a starter topic above, or create a card on Today, and it appears here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                // One session for everything due on the coast, island by island.
                val due = uiState.islands.sumOf { it.dueCount }
                if (due > 0) {
                    item(key = "review", span = { GridItemSpan(maxLineSpan) }) {
                        CoastButton(
                            onClick = { onReviewCoast(uiState.coastId, uiState.coastName) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = ButtonHeight)
                                .animateItem()
                        ) {
                            Text(
                                "Review the coast · $due due",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                items(uiState.islands, key = { it.island.islandId }) { summary ->
                    IslandCard(
                        summary = summary,
                        today = uiState.today ?: return@items,
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
 * A card for one "Language Island": its emoji in a small tile, name, card count and either how many
 * cards are due or when it was last studied, with a ⋮ menu to see its cards or delete it.
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
    ContentCard(onClick = onClick, modifier = modifier.heightIn(min = 160.dp), padding = PaddingValues(start = 14.dp, top = 14.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            IconTile { Text(islandEmoji(island), fontSize = 22.sp) }
            Spacer(modifier = Modifier.weight(1f))
            IslandMenu(islandName = island.name, onShowCards = onShowCards, onDelete = onDeleteClick)
        }
        Spacer(modifier = Modifier.height(12.dp))
        // The card has no end padding, so that the ⋮ button can sit in its corner.
        Column(modifier = Modifier.fillMaxWidth().padding(end = 14.dp)) {
            Text(
                text = island.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(plural(summary.cardCount, "card"), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            if (summary.dueCount > 0) {
                Text("${summary.dueCount} due", style = MaterialTheme.typography.labelMedium, color = Sunlit)
            } else {
                Text(lastStudiedLabel(island.lastStudied, today), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

/** The ⋮ menu of an island card: See cards and Delete island. */
@Composable
private fun IslandMenu(islandName: String, onShowCards: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        // IconButton keeps a 48dp touch target even though the icon is small.
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Options for $islandName", tint = TextSecondary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("See cards") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = null) },
                onClick = {
                    expanded = false
                    onShowCards()
                }
            )
            DropdownMenuItem(
                text = { Text("Delete island") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDelete()
                }
            )
        }
    }
}
