package com.andreaserick.languagecoast.ui.mycoast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.andreaserick.languagecoast.data.Coast
import com.andreaserick.languagecoast.data.CoastSummary
import com.andreaserick.languagecoast.ui.components.LanguagePickerDialog
import com.andreaserick.languagecoast.ui.theme.CoralAccent
import com.andreaserick.languagecoast.ui.theme.DeepOceanBlue
import com.andreaserick.languagecoast.ui.theme.SandBeige

/**
 * The "My Coasts" screen. Shows the study streak and one card per coast (language being studied).
 *
 * @param onCoastClick Called with the coast ID and display name when a coast is tapped.
 */
@Composable
fun MyCoastScreen(
    onCoastClick: (Int, String) -> Unit,
    viewModel: MyCoastViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showNewCoastDialog by remember { mutableStateOf(false) }
    var coastToDelete by remember { mutableStateOf<Coast?>(null) }

    if (showNewCoastDialog) {
        LanguagePickerDialog(
            title = "New Coast",
            subtitle = "Which language do you want to study?",
            languages = uiState.availableLanguages,
            onPick = { language ->
                viewModel.addCoast(language.name)
                showNewCoastDialog = false
            },
            onDismiss = { showNewCoastDialog = false }
        )
    }

    coastToDelete?.let { coast ->
        DeleteCoastDialog(
            coast = coast,
            onConfirm = {
                viewModel.deleteCoast(coast)
                coastToDelete = null
            },
            onDismiss = { coastToDelete = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = "My Coasts",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 32.dp, bottom = 24.dp)
        )

        StreakCard(streakCount = uiState.streakCount)

        Spacer(modifier = Modifier.height(24.dp))

        when {
            // Show nothing while loading to avoid flashing the empty state.
            uiState.isLoading -> Unit
            uiState.coasts.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "You haven't started a coast yet. Pick a language to start your first one!",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { showNewCoastDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Start a Coast")
                }
            }
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.coasts, key = { it.coast.coastId }) { summary ->
                    CoastCard(
                        summary = summary,
                        onClick = { onCoastClick(summary.coast.coastId, summary.coast.displayName) },
                        onDeleteClick = { coastToDelete = summary.coast }
                    )
                }
                if (uiState.availableLanguages.isNotEmpty()) {
                    item {
                        OutlinedButton(
                            onClick = { showNewCoastDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text("New Coast")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteCoastDialog(coast: Coast, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Coast") },
        text = { Text("Are you sure you want to delete the ${coast.displayName}? This will also delete all of its islands and flashcards.") },
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
 * Displays the user's daily study streak.
 *
 * @param streakCount The current number of consecutive study days.
 */
@Composable
fun StreakCard(streakCount: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            contentColor = SandBeige
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (streakCount > 0) "🔥" else "⚓",
                fontSize = 40.sp,
                modifier = Modifier.padding(end = 16.dp)
            )
            Column {
                Text(
                    text = if (streakCount == 1) "1 Day Streak!" else "$streakCount Day Streak!",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (streakCount > 0) "Keep the momentum going!" else "Study an island to start your streak!",
                    fontSize = 14.sp,
                    color = SandBeige.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * A wide card representing one coast, with its island and card counts.
 *
 * @param summary The coast and its counts.
 * @param onClick Called when the card is tapped.
 * @param onDeleteClick Called when the delete button is tapped.
 */
@Composable
fun CoastCard(summary: CoastSummary, onClick: () -> Unit, onDeleteClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 20.dp, end = 56.dp)) {
                Text(
                    text = summary.coast.displayName,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeepOceanBlue
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${plural(summary.islandCount, "island")} · ${plural(summary.cardCount, "card")}",
                    fontSize = 14.sp,
                    color = DeepOceanBlue.copy(alpha = 0.75f)
                )
            }

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Coast",
                    tint = DeepOceanBlue.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

private fun plural(count: Int, noun: String) = if (count == 1) "1 $noun" else "$count ${noun}s"
