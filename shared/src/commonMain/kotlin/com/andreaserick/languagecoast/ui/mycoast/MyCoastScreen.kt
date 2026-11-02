package com.andreaserick.languagecoast.ui.mycoast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andreaserick.languagecoast.data.CoastSummary
import com.andreaserick.languagecoast.data.Languages
import com.andreaserick.languagecoast.ui.components.ButtonHeight
import com.andreaserick.languagecoast.ui.components.CoastButton
import com.andreaserick.languagecoast.ui.components.CoastSecondaryButton
import com.andreaserick.languagecoast.ui.components.ContentCard
import com.andreaserick.languagecoast.ui.components.EmptyState
import com.andreaserick.languagecoast.ui.components.IconTile
import com.andreaserick.languagecoast.ui.components.LanguagePickerDialog
import com.andreaserick.languagecoast.ui.components.LocalUndoMessenger
import com.andreaserick.languagecoast.ui.components.Pill
import com.andreaserick.languagecoast.ui.components.ScreenHeader
import com.andreaserick.languagecoast.ui.components.ScreenMargin
import com.andreaserick.languagecoast.ui.components.dayOfWeekName
import com.andreaserick.languagecoast.ui.components.lastStudiedLabel
import com.andreaserick.languagecoast.ui.components.plural
import com.andreaserick.languagecoast.ui.theme.StreakToday
import com.andreaserick.languagecoast.ui.theme.SurfBlue
import com.andreaserick.languagecoast.ui.theme.SurfaceRaised
import com.andreaserick.languagecoast.ui.theme.Sunlit
import com.andreaserick.languagecoast.ui.theme.TextPrimary
import com.andreaserick.languagecoast.ui.theme.TextSecondary
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.koin.compose.viewmodel.koinViewModel

/**
 * The "My Coasts" screen. Shows the study streak and one card per coast (language being studied).
 *
 * @param onCoastClick Called with the coast ID and display name when a coast is tapped.
 * @param onSearchClick Called when the search icon is tapped.
 */
@Composable
fun MyCoastScreen(
    onCoastClick: (Int, String) -> Unit,
    onSearchClick: () -> Unit,
    viewModel: MyCoastViewModel = koinViewModel()
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
        ScreenHeader(
            title = "My Coasts",
            actions = {
                if (uiState.coasts.any { it.cardCount > 0 }) {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = "Search cards", tint = TextPrimary)
                    }
                }
            }
        )

        when {
            // Show nothing while loading to avoid flashing the empty state.
            uiState.isLoading -> Unit
            uiState.coasts.isEmpty() -> EmptyState(
                icon = Icons.Default.Sailing,
                title = "No coasts yet",
                body = "Pick a language to start your first one.",
                action = {
                    CoastButton(
                        onClick = { showNewCoastDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ButtonHeight)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Start a Coast")
                    }
                }
            )
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = ScreenMargin, end = ScreenMargin, top = 12.dp, bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "streak") {
                    StreakCard(
                        streakCount = uiState.streakCount,
                        studyDays = uiState.studyDays,
                        today = uiState.today
                    )
                }
                items(uiState.coasts, key = { it.coast.coastId }) { summary ->
                    CoastCard(
                        summary = summary,
                        today = uiState.today ?: return@items,
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
                        CoastSecondaryButton(
                            onClick = { showNewCoastDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ButtonHeight)
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

/**
 * The user's daily study streak, with a bar for each day of the current week.
 *
 * @param streakCount The current number of consecutive study days.
 * @param studyDays Days with a completed study session.
 */
@Composable
fun StreakCard(streakCount: Int, studyDays: Set<LocalDate>, today: LocalDate?, modifier: Modifier = Modifier) {
    ContentCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            if (streakCount > 0) {
                Text("$streakCount", style = MaterialTheme.typography.headlineMedium, color = Sunlit)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "day streak",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            } else {
                Column {
                    Text("Start your streak today", style = MaterialTheme.typography.titleMedium)
                    Text("Finish an island to light the fire.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
        }
        if (today != null) {
            Spacer(modifier = Modifier.height(14.dp))
            WeekBars(studyDays = studyDays, today = today)
        }
    }
}

/** A bar for each day from Monday to Sunday of the current week: lit if studied, a step brighter for today. */
@Composable
private fun WeekBars(studyDays: Set<LocalDate>, today: LocalDate) {
    val monday = today.minus(today.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        (0 until 7).map { monday.plus(it, DateTimeUnit.DAY) }.forEach { day ->
            val studied = day in studyDays
            val dayName = dayOfWeekName(day.dayOfWeek, narrow = false)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "$dayName: ${if (studied) "studied" else "not studied"}"
                    }
            ) {
                Text(
                    text = dayOfWeekName(day.dayOfWeek, narrow = true),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (day == today) FontWeight.ExtraBold else FontWeight.Bold,
                    color = if (day == today) TextPrimary else TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(
                            color = when {
                                studied -> Sunlit
                                day == today -> StreakToday
                                else -> SurfaceRaised
                            },
                            shape = RoundedCornerShape(3.dp)
                        )
                )
            }
        }
    }
}

/**
 * A card for one coast: its flag and name (with the native name), how many cards are due,
 * and how much of the coast was explored in the last week. Deleting is in its ⋮ menu.
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
    val language = Languages.byName(coast.language)
    val nativeName = language?.nativeName?.takeIf { it != coast.language }
    val flag = language?.flag
    // The islands are counted on the line under the progress bar, or here if there is no bar yet.
    val counts = if (summary.islandCount > 0) plural(summary.cardCount, "card") else plural(summary.islandCount, "island")

    ContentCard(
        onClick = onClick,
        modifier = modifier,
        padding = PaddingValues(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile {
                if (flag != null) {
                    Text(flag, fontSize = 24.sp)
                } else {
                    // No flag stands for this language, so it shows its code instead.
                    Text(
                        (language?.code ?: coast.language.take(2)).uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(coast.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = listOfNotNull(nativeName, counts).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            if (summary.dueCount > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Pill("${summary.dueCount} due")
            }
            CoastMenu(coastName = coast.displayName, onDelete = onDeleteClick)
        }
        if (summary.islandCount > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { summary.islandsStudiedRecently.toFloat() / summary.islandCount },
                color = SurfBlue,
                trackColor = SurfaceRaised,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 12.dp)
                    .height(4.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${summary.islandsStudiedRecently} of ${plural(summary.islandCount, "island")} this week · " +
                    lastStudiedLabel(summary.lastStudied, today),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(end = 12.dp)
            )
        }
    }
}

/** The ⋮ menu of a coast card. Delete lives here so it can't be hit on the way to opening the coast. */
@Composable
private fun CoastMenu(coastName: String, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Options for $coastName", tint = TextSecondary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Delete coast") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDelete()
                }
            )
        }
    }
}
