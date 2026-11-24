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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andreaserick.languagecoast.data.IslandSummary
import com.andreaserick.languagecoast.data.Level
import com.andreaserick.languagecoast.data.islandLevels
import com.andreaserick.languagecoast.ui.theme.SurfaceRaised
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.graphics.StrokeCap
import com.andreaserick.languagecoast.data.PersonalIslands
import com.andreaserick.languagecoast.data.RouteLevel
import com.andreaserick.languagecoast.data.RouteStop
import com.andreaserick.languagecoast.data.TALK_MIN_CARDS
import com.andreaserick.languagecoast.data.aboutStopId
import com.andreaserick.languagecoast.data.islandEmoji
import com.andreaserick.languagecoast.ui.components.ButtonHeight
import com.andreaserick.languagecoast.ui.components.ButtonCountLabel
import com.andreaserick.languagecoast.ui.components.CoastButton
import com.andreaserick.languagecoast.ui.components.ContentCard
import com.andreaserick.languagecoast.ui.components.IconTile
import com.andreaserick.languagecoast.ui.components.KeepScreenOn
import com.andreaserick.languagecoast.ui.components.LocalUndoMessenger
import com.andreaserick.languagecoast.ui.components.ScreenHeader
import com.andreaserick.languagecoast.ui.components.TooltipIconButton
import com.andreaserick.languagecoast.ui.components.ScreenMargin
import com.andreaserick.languagecoast.ui.components.TileEmoji
import com.andreaserick.languagecoast.ui.components.lastStudiedLabel
import com.andreaserick.languagecoast.ui.components.plural
import com.andreaserick.languagecoast.ui.theme.CoralText
import com.andreaserick.languagecoast.ui.theme.Seafoam
import com.andreaserick.languagecoast.ui.theme.Sunlit
import com.andreaserick.languagecoast.ui.theme.SunlitText
import com.andreaserick.languagecoast.ui.theme.TextPrimary
import com.andreaserick.languagecoast.ui.theme.TextSecondary
import com.andreaserick.languagecoast.ui.theme.tabular
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel

/** Deleting an island with more cards than this asks first; a smaller one is deleted at once and can be taken back with Undo. */
private const val CONFIRM_DELETE_ABOVE_CARDS = 10

/**
 * A single coast: its route through the levels (where the user is, and the next island to take), a
 * button to review what is due, and a grid of its "Language Islands" (categories) that contain flashcards.
 * The PDF icon in the header makes a practice sheet from its cards. Deleting an island with more than
 * [CONFIRM_DELETE_ABOVE_CARDS] cards asks first.
 *
 * @param onIslandClick Called with the island ID and name when an island is tapped.
 * @param onReviewCoast Called with the coast ID and name to review every due card on the coast.
 * @param onShowCards Called with the island ID and name when See cards is picked in an island's menu.
 * @param onAboutYou Opens the questions about the user, at the island about them with this id (or the top, if empty).
 * @param onNavigateBack Called when the back arrow is tapped.
 */
@Composable
fun CoastScreen(
    onIslandClick: (Int, String) -> Unit,
    onReviewCoast: (Int, String) -> Unit,
    onShowCards: (Int, String) -> Unit,
    onAboutYou: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: CoastViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val undoMessenger = LocalUndoMessenger.current
    // What is open is kept through a rotation and the app being put away. A sheet is kept as the names of its
    // topics or the ids of its route stops; what it shows is looked up in the state.
    var topicSheet by rememberSaveable { mutableStateOf<List<String>?>(null) }
    var personalSheet by rememberSaveable { mutableStateOf<List<String>?>(null) }
    var showWorksheet by rememberSaveable { mutableStateOf(false) }
    var showRoute by rememberSaveable { mutableStateOf(false) }
    var showReview by rememberSaveable { mutableStateOf(false) }
    // The id of the island waiting for a yes before it is deleted.
    var pendingDelete by rememberSaveable { mutableStateOf<Int?>(null) }
    val worksheetFiles = rememberWorksheetFiles()
    // Topics are added and islands written one run at a time, app-wide: a run on another coast holds this one back too.
    val busy = uiState.isBuilding || uiState.import?.isFinished == false || uiState.isBusyAnywhere
    // That run's progress card is on the other coast, so this one has to say why its route waits.
    val busyElsewhere = uiState.isBusyAnywhere && !uiState.isBuilding && uiState.import?.isFinished != false

    // A practice sheet takes minutes and stops when the screen goes dark.
    if (uiState.worksheet.isCreating) KeepScreenOn()

    /** What writing the island behind [stop] would be, or null for a stop that isn't written (a starter topic). */
    fun optionFor(stop: RouteStop): PersonalIslandOption? {
        val islandId = stop.id.removePrefix("about:").substringBefore('@')
        val island = PersonalIslands.byId(islandId) ?: return null
        return PersonalIslandOption(
            island = island,
            state = if (stop.state == RouteStop.State.Changed) PersonalIslandState.Changed else PersonalIslandState.Ready,
            facts = uiState.islandFacts[islandId].orEmpty(),
            raiseTo = stop.level.takeIf { stop.kind == RouteStop.Kind.LevelUp }
        )
    }

    /** Takes a stop of the route: adds the topic, writes the island, or opens its questions. */
    fun take(stop: RouteStop) {
        showRoute = false
        when {
            stop.kind == RouteStop.Kind.Topic -> {
                if (!busy && uiState.starterTopics.any { it.theme.category == stop.name }) topicSheet = listOf(stop.name)
            }
            // An island can only be taken a level up once it is on the coast: take the island itself first.
            stop.kind == RouteStop.Kind.LevelUp && stop.state == RouteStop.State.Locked ->
                uiState.route?.stop(aboutStopId(stop.id.removePrefix("about:").substringBefore('@')))?.let { take(it) }
            stop.state == RouteStop.State.Locked -> onAboutYou(stop.id.removePrefix("about:"))
            else -> {
                // One run at a time: a sheet opened now would close without writing anything.
                if (!busy && optionFor(stop) != null) personalSheet = listOf(stop.id)
            }
        }
    }

    fun addTopics(level: RouteLevel) {
        showRoute = false
        val wanted = level.stops.filter { it.kind == RouteStop.Kind.Topic && it.state == RouteStop.State.Ready }.mapTo(HashSet()) { it.name }
        uiState.starterTopics.map { it.theme.category }.filter { it in wanted }.takeIf { it.isNotEmpty() }?.let { topicSheet = it }
    }

    fun writeAll(level: RouteLevel) {
        showRoute = false
        level.stops
            .filter { it.kind != RouteStop.Kind.Topic && (it.state == RouteStop.State.Ready || it.state == RouteStop.State.Changed) }
            .filter { optionFor(it) != null }
            .map { it.id }
            .takeIf { it.isNotEmpty() }?.let { personalSheet = it }
    }

    /** Deletes the island right away, with a way to take it back. */
    fun delete(summary: IslandSummary) {
        viewModel.deleteIsland(summary.island) { deleted ->
            undoMessenger.show("${summary.island.name} deleted") { viewModel.restore(deleted) }
        }
    }

    if (showWorksheet) {
        WorksheetSheet(
            state = uiState.worksheet,
            coastLanguage = uiState.coastLanguage,
            nativeLanguage = uiState.nativeLanguage,
            islands = uiState.islands,
            provider = uiState.translationProvider,
            files = worksheetFiles,
            onOptionsChange = viewModel::setWorksheetOptions,
            onCreate = viewModel::createWorksheet,
            onCancel = viewModel::cancelWorksheet,
            onStartNew = viewModel::startNewWorksheet,
            onDismiss = { showWorksheet = false }
        )
    }

    val route = uiState.route

    topicSheet?.let { wanted -> uiState.starterTopics.filter { it.theme.category in wanted } }?.takeIf { it.isNotEmpty() }?.let { options ->
        StarterTopicSheet(
            options = options,
            coastLanguage = uiState.coastLanguage,
            provider = uiState.translationProvider,
            problem = uiState.engineProblem,
            profile = uiState.profile,
            onAdd = { fillers ->
                viewModel.addStarterTopics(options.map { it.theme.category }, fillers)
                topicSheet = null
            },
            onDismiss = { topicSheet = null }
        )
    }

    personalSheet?.mapNotNull { stopId -> route?.stop(stopId)?.let(::optionFor) }?.takeIf { it.isNotEmpty() }?.let { options ->
        PersonalIslandSheet(
            options = options,
            coastLanguage = uiState.coastLanguage,
            nativeLanguage = uiState.nativeLanguage,
            provider = uiState.translationProvider,
            problem = uiState.engineProblem,
            onWrite = {
                // What an earlier run left to look over is gone once this one starts.
                showReview = false
                viewModel.buildIslands(options.map { it.order })
                personalSheet = null
            },
            onAboutYou = { islandId ->
                personalSheet = null
                onAboutYou(islandId)
            },
            onDismiss = { personalSheet = null }
        )
    }

    val islandLevels = remember(route) { route?.islandLevels().orEmpty() }
    if (showRoute && route != null) {
        RouteSheet(
            route = route,
            busy = busy,
            canWrite = uiState.canWrite,
            onStop = ::take,
            onAddTopics = ::addTopics,
            onWriteAll = ::writeAll,
            onDismiss = { showRoute = false }
        )
    }

    val hasReview = uiState.buildReview.added.isNotEmpty() || uiState.buildReview.kept.isNotEmpty()
    if (showReview && hasReview) {
        BuildReviewSheet(
            review = uiState.buildReview,
            coastLanguage = uiState.coastLanguage,
            onRemove = { cardIds ->
                viewModel.removeCards(cardIds) { removed ->
                    undoMessenger.show("${plural(removed.cards.size, "card")} removed") { viewModel.restore(removed) }
                }
            },
            onDismiss = { showReview = false }
        )
    }

    uiState.talk?.let { talk ->
        TalkSheet(
            state = talk,
            coastLanguage = uiState.coastLanguage,
            onSpeak = viewModel::speakTalk,
            onAnother = { viewModel.writeTalk(talk.island) },
            onDismiss = viewModel::dismissTalk
        )
    }

    uiState.islands.firstOrNull { it.island.islandId == pendingDelete }?.let { summary ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${summary.island.name}?") },
            text = { Text("${plural(summary.cardCount, "card")} and their progress will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    delete(summary)
                }) { Text("Delete", color = CoralText, style = MaterialTheme.typography.labelMedium) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel", color = TextPrimary, style = MaterialTheme.typography.labelMedium) }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = uiState.coastName,
            subtitle = uiState.nativeName,
            onBack = onNavigateBack,
            actions = {
                // A sheet is made from the coast's cards, so a coast without islands has nothing to offer yet.
                if (uiState.canPrintWorksheets && uiState.islands.isNotEmpty()) {
                    TooltipIconButton(icon = Icons.Default.PictureAsPdf, label = "Print a practice sheet", onClick = { showWorksheet = true })
                }
            }
        )

        // Above the grid: a lazy grid keeps its first visible item in place, so a card inserted at its top would land out of view.
        uiState.import?.let { progress ->
            ImportProgressCard(
                progress = progress,
                onStop = viewModel::stopImport,
                onDismiss = viewModel::dismissImport,
                modifier = Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = 12.dp)
            )
        }

        uiState.build?.let { progress ->
            BuildProgressCard(
                progress = progress,
                onStop = viewModel::stopBuild,
                onReview = { showReview = true }.takeIf { uiState.buildReview.added.isNotEmpty() || uiState.buildReview.kept.isNotEmpty() },
                onDismiss = viewModel::dismissBuild,
                modifier = Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = 12.dp)
            )
        }

        // Show nothing while loading to avoid flashing the empty state.
        if (!uiState.isLoading) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = ScreenMargin, end = ScreenMargin, top = 12.dp, bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                val reached = uiState.levelReached
                if (reached != null) {
                    item(key = "reached", span = { GridItemSpan(maxLineSpan) }) {
                        LevelReachedCard(
                            level = reached,
                            next = Level.entries.getOrNull(reached.ordinal + 1),
                            onDismiss = { viewModel.celebrated(reached) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
                if (route != null) {
                    item(key = "route", span = { GridItemSpan(maxLineSpan) }) {
                        RouteCard(
                            route = route,
                            waiting = uiState.islands.sumOf { it.newCount },
                            pace = uiState.pace,
                            busy = busy,
                            busyElsewhere = busyElsewhere,
                            canWrite = uiState.canWrite,
                            onNext = ::take,
                            onOpen = { showRoute = true },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
                if (uiState.islands.isEmpty()) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "No islands yet. Take the first one on your route above, or create a card on Today, and it appears here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                // One session for everything due on the coast, island by island. New cards aren't in it: they come a few a day.
                val due = uiState.islands.sumOf { it.reviewCount }
                if (due > 0) {
                    item(key = "review", span = { GridItemSpan(maxLineSpan) }) {
                        CoastButton(
                            onClick = { onReviewCoast(uiState.coastId, uiState.coastName) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = ButtonHeight)
                                .animateItem()
                        ) {
                            ButtonCountLabel("Review the coast", due, "$due due")
                        }
                    }
                }
                items(uiState.islands, key = { it.island.islandId }) { summary ->
                    IslandCard(
                        summary = summary,
                        level = islandLevels[summary.island.name],
                        today = uiState.today ?: return@items,
                        onClick = { onIslandClick(summary.island.islandId, summary.island.name) },
                        onShowCards = { onShowCards(summary.island.islandId, summary.island.name) },
                        onTalk = { viewModel.writeTalk(summary.island) }.takeIf { uiState.canWrite && summary.cardCount >= TALK_MIN_CARDS },
                        // A small island goes at once, with Undo; one with many cards asks first.
                        onDeleteClick = {
                            if (summary.cardCount > CONFIRM_DELETE_ABOVE_CARDS) {
                                pendingDelete = summary.island.islandId
                            } else {
                                delete(summary)
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
 * A card for one "Language Island": its emoji in a small tile, name, card count and what there is to do
 * on it (cards due, new cards waiting) or when it was last studied, with Study and Cards buttons, and a ⋮
 * menu to see its cards, rehearse it as a talk or delete it.
 *
 * @param level The level the island belongs to on the coast's route; null for an island of the user's own.
 * @param onTalk Turns the island into a talk to rehearse; null when it can't be (no AI model, or too few cards).
 */
@Composable
fun IslandCard(
    summary: IslandSummary,
    level: Level?,
    today: LocalDate,
    onClick: () -> Unit,
    onShowCards: () -> Unit,
    onTalk: (() -> Unit)?,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val island = summary.island
    ContentCard(onClick = onClick, onClickLabel = "study", modifier = modifier.heightIn(min = 160.dp), padding = PaddingValues(start = 14.dp, top = 14.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            IconTile { TileEmoji(islandEmoji(island)) }
            Spacer(modifier = Modifier.weight(1f))
            IslandMenu(islandName = island.name, onShowCards = onShowCards, onTalk = onTalk, onDelete = onDeleteClick)
        }
        Spacer(modifier = Modifier.height(12.dp))
        // The card has no end padding, so that the ⋮ button can sit in its corner.
        Column(modifier = Modifier.fillMaxWidth().padding(end = 14.dp)) {
            Text(
                text = island.name,
                style = MaterialTheme.typography.titleMedium,
                // Two lines kept even for a short name, so cards side by side in the grid are the same height.
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            // "A1 Beginner" rather than a bare "A1", which means nothing to a newcomer. Too long for the top row of a
            // narrow grid card, so it has its own line; an empty one for an island of the user's own keeps the
            // cards side by side the same height.
            Text(
                text = level?.shortLabel.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                minLines = 1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(plural(summary.cardCount, "card"), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            when {
                summary.reviewCount > 0 -> Text("${summary.reviewCount} due", style = MaterialTheme.typography.labelMedium.tabular, color = SunlitText)
                summary.newCount > 0 -> Text("${summary.newCount} new", style = MaterialTheme.typography.labelMedium.tabular, color = Seafoam)
                else -> Text(lastStudiedLabel(island.lastStudied, today), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            // How much of the island is learned: the cards studied at least once.
            if (summary.cardCount > 0) {
                val learned = (summary.cardCount - summary.newCount).toFloat() / summary.cardCount
                LinearProgressIndicator(
                    progress = { learned },
                    color = if (learned >= 1f) Seafoam else Sunlit,
                    trackColor = SurfaceRaised,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(4.dp)
                )
            }
        }
        // Tapping the card studies it; both actions are spelled out so "see the cards" isn't hidden in the ⋮ menu.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            // Pulled back by the buttons' own padding, so "Study" lines up with the island's name.
            modifier = Modifier.fillMaxWidth().offset(x = (-8).dp).padding(top = 2.dp)
        ) {
            TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Study", style = MaterialTheme.typography.labelMedium, color = SunlitText, maxLines = 1)
            }
            TextButton(onClick = onShowCards, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cards", style = MaterialTheme.typography.labelMedium, color = TextPrimary, maxLines = 1)
            }
        }
    }
}

/** The ⋮ menu of an island card: See cards, Rehearse as a talk and Delete island. */
@Composable
private fun IslandMenu(islandName: String, onShowCards: () -> Unit, onTalk: (() -> Unit)?, onDelete: () -> Unit) {
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
            if (onTalk != null) {
                DropdownMenuItem(
                    text = { Text("Rehearse as a talk") },
                    leadingIcon = { Icon(Icons.Default.RecordVoiceOver, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onTalk()
                    }
                )
            }
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
