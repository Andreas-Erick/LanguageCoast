package com.andreaserick.languagecoast.ui.mycoast

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andreaserick.languagecoast.data.CoastSummary
import com.andreaserick.languagecoast.data.Languages
import com.andreaserick.languagecoast.ui.components.LanguagePickerDialog
import com.andreaserick.languagecoast.ui.components.LocalUndoMessenger
import com.andreaserick.languagecoast.ui.components.ScreenHeader
import com.andreaserick.languagecoast.ui.components.lastStudiedLabel
import com.andreaserick.languagecoast.ui.components.plural
import com.andreaserick.languagecoast.ui.theme.CardWash
import com.andreaserick.languagecoast.ui.theme.DeepOceanBlue
import com.andreaserick.languagecoast.ui.theme.MistWhite
import com.andreaserick.languagecoast.ui.theme.SandBeige
import com.andreaserick.languagecoast.ui.theme.SandMuted
import com.andreaserick.languagecoast.ui.theme.cardTint
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

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
    val undoMessenger = LocalUndoMessenger.current

    var showNewCoastDialog by remember { mutableStateOf(false) }

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

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "My Coasts")

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            item(key = "streak") {
                StreakCard(
                    streakCount = uiState.streakCount,
                    studyDays = uiState.studyDays,
                    today = uiState.today,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            when {
                // Show nothing while loading to avoid flashing the empty state.
                uiState.isLoading -> Unit
                uiState.coasts.isEmpty() -> item(key = "empty") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp)
                    ) {
                        Text("⛵", fontSize = 56.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "You haven't started a coast yet. Pick a language to start your first one!",
                            color = SandBeige,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { showNewCoastDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text("Start a Coast")
                        }
                    }
                }
                else -> {
                    items(uiState.coasts, key = { it.coast.coastId }) { summary ->
                        CoastCard(
                            summary = summary,
                            today = uiState.today,
                            onClick = { onCoastClick(summary.coast.coastId, summary.coast.displayName) },
                            onDeleteClick = {
                                viewModel.deleteCoast(summary.coast) { deleted ->
                                    undoMessenger.show("${summary.coast.displayName} deleted") { viewModel.restore(deleted) }
                                }
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                    if (uiState.availableLanguages.isNotEmpty()) {
                        item(key = "new-coast") {
                            OutlinedButton(
                                onClick = { showNewCoastDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 24.dp)
                                    .animateItem()
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
}

/**
 * The user's daily study streak, with a dot for each day of the current week.
 *
 * @param streakCount The current number of consecutive study days.
 * @param studyDays Days with a completed study session.
 */
@Composable
fun StreakCard(streakCount: Int, studyDays: Set<LocalDate>, today: LocalDate, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardWash, contentColor = SandBeige),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (streakCount > 0) "🔥" else "⚓",
                    fontSize = 36.sp,
                    modifier = Modifier.padding(end = 16.dp)
                )
                Column {
                    Text(
                        text = when (streakCount) {
                            0 -> "Start your streak today"
                            1 -> "1 day streak!"
                            else -> "$streakCount day streak!"
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = if (streakCount > 0) "Keep the momentum going!" else "Finish an island to light the fire.",
                        fontSize = 14.sp,
                        color = MistWhite
                    )
                }
            }
            if (today != LocalDate.MIN) {
                Spacer(modifier = Modifier.height(16.dp))
                WeekDots(studyDays = studyDays, today = today)
            }
        }
    }
}

/** A dot for each day from Monday to Sunday of the current week: filled if studied, outlined for today. */
@Composable
private fun WeekDots(studyDays: Set<LocalDate>, today: LocalDate) {
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        (0L until 7L).map { monday.plusDays(it) }.forEach { day ->
            val studied = day in studyDays
            val dayName = day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.semantics {
                    contentDescription = "$dayName: ${if (studied) "studied" else "not studied"}"
                }
            ) {
                Text(
                    text = day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    fontSize = 12.sp,
                    fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal,
                    color = if (day == today) SandBeige else MistWhite
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .then(
                            when {
                                studied -> Modifier.background(SandBeige, CircleShape)
                                day == today -> Modifier.border(2.dp, SandBeige, CircleShape)
                                else -> Modifier.border(1.dp, MistWhite.copy(alpha = 0.4f), CircleShape)
                            }
                        )
                ) {
                    if (studied) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = DeepOceanBlue, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

/**
 * A card for one coast: its language (with the native name), counts, last study session
 * and how many islands were explored in the last week.
 */
@Composable
fun CoastCard(
    summary: CoastSummary,
    today: LocalDate,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coast = summary.coast
    val nativeName = Languages.byName(coast.language)?.nativeName?.takeIf { it != coast.language }
    val onCard = DeepOceanBlue
    val onCardSecondary = DeepOceanBlue.copy(alpha = 0.85f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = cardTint(coast.coastId), contentColor = onCard)
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 20.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f).padding(top = 4.dp)) {
                    Text(text = coast.displayName, style = MaterialTheme.typography.titleLarge)
                    if (nativeName != null) {
                        Text(text = nativeName, fontSize = 14.sp, color = onCardSecondary)
                    }
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete ${coast.displayName}", tint = onCardSecondary)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "${plural(summary.islandCount, "island")} · ${plural(summary.cardCount, "card")} · " +
                    lastStudiedLabel(summary.lastStudied, today),
                fontSize = 14.sp,
                color = onCardSecondary,
                modifier = Modifier.padding(end = 16.dp)
            )
            if (summary.islandCount > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { summary.islandsStudiedRecently.toFloat() / summary.islandCount },
                    color = DeepOceanBlue,
                    trackColor = DeepOceanBlue.copy(alpha = 0.15f),
                    strokeCap = StrokeCap.Round,
                    drawStopIndicator = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 16.dp)
                        .height(6.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${summary.islandsStudiedRecently} of ${plural(summary.islandCount, "island")} explored this week",
                    fontSize = 12.sp,
                    color = onCardSecondary
                )
            }
        }
    }
}
