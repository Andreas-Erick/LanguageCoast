package com.andreaserick.languagecoast.ui.coast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andreaserick.languagecoast.data.LanguageIsland
import com.andreaserick.languagecoast.ui.theme.CoralAccent
import com.andreaserick.languagecoast.ui.theme.DeepOceanBlue
import com.andreaserick.languagecoast.ui.theme.SandBeige

/**
 * A single coast: a grid of its "Language Islands" (categories) that contain flashcards.
 *
 * @param onIslandClick Called with the island ID and name when an island is tapped.
 * @param onNavigateBack Called when the back arrow is tapped.
 */
@Composable
fun CoastScreen(
    onIslandClick: (Int, String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: CoastViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var islandToDelete by remember { mutableStateOf<LanguageIsland?>(null) }

    islandToDelete?.let { island ->
        DeleteIslandDialog(
            island = island,
            onConfirm = {
                viewModel.deleteIsland(island)
                islandToDelete = null
            },
            onDismiss = { islandToDelete = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 24.dp, bottom = 24.dp)
        ) {
            IconButton(onClick = onNavigateBack, modifier = Modifier.padding(end = 4.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SandBeige)
            }
            Text(
                text = uiState.coastName,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        when {
            // Show nothing while loading to avoid flashing the empty state.
            uiState.isLoading -> Unit
            uiState.islands.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "This coast has no islands yet. Go create some flashcards!",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.islands, key = { it.islandId }) { island ->
                    IslandCard(
                        island = island,
                        onClick = { onIslandClick(island.islandId, island.name) },
                        onDeleteClick = { islandToDelete = island }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeleteIslandDialog(island: LanguageIsland, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Island") },
        text = { Text("Are you sure you want to delete '${island.name}'? This will also delete all associated flashcards.") },
        containerColor = DeepOceanBlue,
        titleContentColor = SandBeige,
        textContentColor = Color.White,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = CoralAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SandBeige)
            }
        }
    )
}

/**
 * A square card representing a single "Language Island".
 *
 * @param island The [LanguageIsland] to display.
 * @param onClick Called when the card is tapped.
 * @param onDeleteClick Called when the delete button is tapped.
 */
@Composable
fun IslandCard(island: LanguageIsland, onClick: () -> Unit, onDeleteClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Island",
                    tint = DeepOceanBlue.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = island.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = DeepOceanBlue,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
            )
        }
    }
}
