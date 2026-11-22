package com.andreaserick.languagecoast.ui.coast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.andreaserick.languagecoast.data.Level
import com.andreaserick.languagecoast.data.Pace
import com.andreaserick.languagecoast.data.Route
import com.andreaserick.languagecoast.data.RouteLevel
import com.andreaserick.languagecoast.data.RouteStop
import com.andreaserick.languagecoast.data.daysToLearn
import com.andreaserick.languagecoast.ui.components.ButtonHeight
import com.andreaserick.languagecoast.ui.components.CoastButton
import com.andreaserick.languagecoast.ui.components.CoastSecondaryButton
import com.andreaserick.languagecoast.ui.components.ContentCard
import com.andreaserick.languagecoast.ui.components.IconTile
import com.andreaserick.languagecoast.ui.components.TileEmoji
import com.andreaserick.languagecoast.ui.components.inLanguage
import com.andreaserick.languagecoast.ui.components.plural
import com.andreaserick.languagecoast.ui.components.roughDuration
import com.andreaserick.languagecoast.ui.theme.OnSeafoam
import com.andreaserick.languagecoast.ui.theme.OnSunlit
import com.andreaserick.languagecoast.ui.theme.Seafoam
import com.andreaserick.languagecoast.ui.theme.SurfaceRaised
import com.andreaserick.languagecoast.ui.theme.Sunlit
import com.andreaserick.languagecoast.ui.theme.SunlitText
import com.andreaserick.languagecoast.ui.theme.TextPrimary
import com.andreaserick.languagecoast.ui.theme.TextSecondary
import com.andreaserick.languagecoast.ui.theme.tabular
import kotlin.math.roundToInt

/** "34%". */
private fun percent(share: Float): String = "${(share * 100).roundToInt()}%"

/** A thin bar for how much of something is done. */
@Composable
private fun ProgressBar(share: Float, modifier: Modifier = Modifier, color: Color = Sunlit) {
    LinearProgressIndicator(
        progress = { share.coerceIn(0f, 1f) },
        color = color,
        trackColor = SurfaceRaised,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
        modifier = modifier.fillMaxWidth().height(6.dp)
    )
}

/**
 * The coast's route at a glance, above its islands: the level the user is on and how far, the three levels
 * side by side, and the next island to take, one tap away.
 *
 * @param waiting Cards on the coast that were never studied, to say how long they take at [pace].
 * @param busy Whether islands are being added or written right now, so the next one has to wait.
 * @param canWrite Whether an AI model is set up that can write islands; without one the card says why the route stops at the starter topics.
 * @param onNext Takes the [Route.next] stop.
 * @param onOpen Opens the whole route.
 * @param busyElsewhere Whether that run is on another coast, where its progress shows; the card then says why the next island waits.
 */
@Composable
internal fun RouteCard(
    route: Route,
    waiting: Int,
    pace: Pace,
    busy: Boolean,
    canWrite: Boolean,
    onNext: (RouteStop) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    busyElsewhere: Boolean = false
) {
    val current = route.current
    val next = route.next
    ContentCard(modifier = modifier, onClick = onOpen, onClickLabel = "see the whole route", padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Level ${current.level.label} · ${current.level.title}", style = MaterialTheme.typography.titleMedium)
                Text(current.level.can, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(percent(current.progress), style = MaterialTheme.typography.headlineSmall.tabular, color = SunlitText)
        }
        LevelStepper(route = route, modifier = Modifier.padding(top = 16.dp))
        if (waiting > 0) {
            Text(
                text = "${plural(waiting, "new card")} waiting on your islands: ${roughDuration(daysToLearn(waiting, pace))} at the ${pace.label.lowercase()} pace.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        // With on-device translation the level stops growing once its starter topics are learned: say why, and what helps.
        if (!canWrite && next != null && next.kind != RouteStop.Kind.Topic) {
            Text(
                text = "The next islands are written by an AI model, which on-device translation can't do. Pick an AI provider under Settings › Translation to go on.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        // A run on another coast greys the button below, and its progress card is over there: say why this one waits.
        if (busyElsewhere && next != null) {
            Text(
                text = "Islands are being added on another coast. Take the next one when that is done.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            if (next != null) {
                CoastSecondaryButton(onClick = { onNext(next) }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = ButtonHeight)) {
                    Text(
                        text = when (next.state) {
                            RouteStop.State.Locked -> "Next: ${next.emoji} ${next.name}"
                            RouteStop.State.Changed -> "Update ${next.emoji} ${next.name}"
                            else -> "Next: ${next.emoji} ${next.name}"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // 8dp at least between two things to tap, so the one next to it isn't hit by mistake.
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Text("Every island of ${current.level.label} is on your coast.", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1f))
            }
            TextButton(onClick = onOpen) { Text("Route", style = MaterialTheme.typography.labelMedium, color = SunlitText) }
        }
    }
}

/**
 * The levels as a path: a marker per level (ticked once reached, lit for the one the user is on) and
 * between them a line that fills as the level before it is learned, ending in a flag. Under each stretch,
 * how many of the level's islands are on the coast.
 */
@Composable
internal fun LevelStepper(route: Route, modifier: Modifier = Modifier) {
    val current = route.current.level
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            route.levels.forEach { level ->
                LevelMarker(level = level.level, reached = level.isComplete, current = level.level == current)
                ProgressBar(
                    share = level.progress,
                    color = if (level.isComplete) Seafoam else Sunlit,
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                )
            }
            Icon(
                imageVector = Icons.Default.Flag,
                contentDescription = null,
                tint = if (route.levels.all { it.isComplete }) Seafoam else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp, end = 20.dp)) {
            route.levels.forEach { level ->
                Text(
                    text = if (level.isComplete) "Reached" else "${level.onCoast} of ${level.stops.size} islands",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (level.level == current) TextPrimary else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** A level's marker on the path: its name in a disc, ticked once reached. */
@Composable
internal fun LevelMarker(level: Level, reached: Boolean, current: Boolean, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(32.dp)
            .background(
                color = when {
                    reached -> Seafoam
                    current -> Sunlit
                    else -> SurfaceRaised
                },
                shape = CircleShape
            )
    ) {
        if (reached) Icon(Icons.Default.Check, contentDescription = "${level.label} reached", tint = OnSeafoam, modifier = Modifier.size(18.dp))
        else Text(level.label, style = MaterialTheme.typography.labelSmall, color = if (current) OnSunlit else TextSecondary)
    }
}

/**
 * The moment a level's islands are learned, at the top of the coast until it is acknowledged: what the user
 * now has the sentences for, and what comes next. It speaks of the islands and their sentences, not of having
 * reached the level: the app counts cards studied, it doesn't test what the user can do.
 *
 * @param next The level after it, or null when it was the last.
 */
@Composable
internal fun LevelReachedCard(level: Level, next: Level?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    ContentCard(modifier = modifier, padding = PaddingValues(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp).background(Seafoam, CircleShape)) {
                Text(level.label, style = MaterialTheme.typography.titleMedium, color = OnSeafoam)
            }
            Spacer(modifier = Modifier.width(14.dp))
            // Read out by a screen reader when the card appears: nothing was tapped to bring it up.
            Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                Text("You've learned the ${level.label} islands 🎉", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "${level.title}. You now have the sentences to ${level.can.replaceFirstChar { it.lowercase() }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
        Text(
            text = if (next != null) "Next is ${next.label}, ${next.title.lowercase()}: ${next.can.replaceFirstChar { it.lowercase() }}"
            else "That is every level on the route. Keep the islands alive with your daily session, and add your own.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp, end = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onDismiss) { Text(if (next != null) "On to ${next.label}" else "Done", style = MaterialTheme.typography.labelMedium, color = SunlitText) }
        }
    }
}

/**
 * What a stop's row says under its name. Without an AI model ([canWrite] false) an island that has to be
 * written says that it needs one, so the route shows which of its stops on-device translation can't take.
 */
private fun stopStatus(stop: RouteStop, canWrite: Boolean): String =
    if (!canWrite && stop.kind != RouteStop.Kind.Topic && stop.state != RouteStop.State.Added) "Needs an AI provider"
    else when (stop.state) {
        RouteStop.State.Added -> when {
            stop.cards == 0 -> "On your coast"
            stop.learned >= stop.cards -> "${plural(stop.cards, "card")}, all learned"
            else -> "${stop.learned} of ${plural(stop.cards, "card")} learned"
        }
        RouteStop.State.Changed -> "Your answers changed: write it again"
        RouteStop.State.Ready -> when (stop.kind) {
            RouteStop.Kind.Topic -> "Starter topic, translated for you"
            RouteStop.Kind.About -> "Written from your answers"
            RouteStop.Kind.Written -> "Written by your AI provider"
            RouteStop.Kind.LevelUp -> "The same answers, with more to say"
        }
        RouteStop.State.Locked -> if (stop.kind == RouteStop.Kind.LevelUp) "Comes after the island itself" else "Answer its questions first"
    }

/**
 * The whole route: every level with what it lets you do, how far it is, and its islands, each with where
 * it stands. Tapping one takes it: a topic is added, an island written, or its questions opened.
 *
 * @param busy Whether islands are being added or written right now.
 * @param canWrite Whether an AI model is set up that can write islands. Without one the sheet says which stops need it.
 * @param onStop Takes [RouteStop]; the sheet closes first.
 * @param onAddTopics Adds every starter topic of a level that isn't on the coast.
 * @param onWriteAll Writes every island of a level that can be written now.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RouteSheet(
    route: Route,
    busy: Boolean,
    canWrite: Boolean,
    onStop: (RouteStop) -> Unit,
    onAddTopics: (RouteLevel) -> Unit,
    onWriteAll: (RouteLevel) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp), modifier = Modifier.fillMaxWidth()) {
            item(key = "title") {
                Text("Your route", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(
                    text = "Each level is a set of islands. Add them, learn their cards, and the next level opens up. Nothing stops you from taking an island early.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (!canWrite) {
                    Text(
                        text = "Islands about you and all of ${Level.B1.label} are written by an AI model. On-device translation takes you through the starter topics; " +
                            "for the rest, pick an AI provider under Settings › Translation.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            route.levels.forEach { level ->
                item(key = "level-${level.level.name}") { LevelHeader(level, busy = busy, onAddTopics = { onAddTopics(level) }, onWriteAll = { onWriteAll(level) }) }
                items(level.stops, key = { it.id }) { stop ->
                    // A locked row only opens its questions, so it stays open during a run; a locked level-up leads to
                    // its island's write sheet, and waits like the rows that write.
                    val opensQuestions = stop.state == RouteStop.State.Locked && stop.kind != RouteStop.Kind.LevelUp
                    StopRow(stop, status = stopStatus(stop, canWrite), enabled = !busy || opensQuestions, onClick = { onStop(stop) })
                }
            }
        }
    }
}

@Composable
private fun LevelHeader(level: RouteLevel, busy: Boolean, onAddTopics: () -> Unit, onWriteAll: () -> Unit) {
    val topics = level.stops.count { it.kind == RouteStop.Kind.Topic && it.state == RouteStop.State.Ready }
    val writable = level.stops.count { it.kind != RouteStop.Kind.Topic && (it.state == RouteStop.State.Ready || it.state == RouteStop.State.Changed) }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).background(if (level.isComplete) Seafoam else Sunlit, CircleShape)
            ) {
                Text(level.level.label, style = MaterialTheme.typography.labelLarge, color = if (level.isComplete) OnSeafoam else OnSunlit)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("${level.level.title} · ${percent(level.progress)}", style = MaterialTheme.typography.titleMedium.tabular, modifier = Modifier.semantics { heading() })
                Text(level.level.can, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        ProgressBar(level.progress, color = if (level.isComplete) Seafoam else Sunlit, modifier = Modifier.padding(top = 10.dp))
        if (topics > 1 || writable > 1) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                if (topics > 1) {
                    TextButton(onClick = onAddTopics, enabled = !busy, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text("Add ${plural(topics, "topic")}", style = MaterialTheme.typography.labelMedium, color = if (busy) TextSecondary else SunlitText)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }
                if (writable > 1) {
                    TextButton(onClick = onWriteAll, enabled = !busy, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text("Write ${plural(writable, "island")}", style = MaterialTheme.typography.labelMedium, color = if (busy) TextSecondary else SunlitText)
                    }
                }
            }
        }
    }
}

/** One island of a level: its tile and name, [status] under it (see [stopStatus]), and what a tap does as an icon. */
@Composable
private fun StopRow(stop: RouteStop, status: String, enabled: Boolean, onClick: () -> Unit) {
    val done = stop.state == RouteStop.State.Added
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled && !done, role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        IconTile(size = 40.dp) { TileEmoji(stop.emoji, tileSize = 40.dp) }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (stop.kind == RouteStop.Kind.LevelUp) "${stop.name} · ${stop.level.shortLabel}" else stop.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (stop.state == RouteStop.State.Locked) TextSecondary else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(status, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (done && stop.cards > 0 && stop.learned < stop.cards) {
                ProgressBar(stop.progress, modifier = Modifier.padding(top = 6.dp, end = 24.dp))
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        when (stop.state) {
            RouteStop.State.Added -> Icon(Icons.Default.Check, contentDescription = "On your coast", tint = Seafoam, modifier = Modifier.size(20.dp))
            RouteStop.State.Changed -> Icon(Icons.Default.Refresh, contentDescription = "Write again", tint = SunlitText, modifier = Modifier.size(20.dp))
            RouteStop.State.Ready -> Icon(Icons.Default.Add, contentDescription = "Add", tint = if (enabled) SunlitText else TextSecondary, modifier = Modifier.size(22.dp))
            RouteStop.State.Locked -> Icon(
                imageVector = if (stop.kind == RouteStop.Kind.LevelUp) Icons.Default.Lock else Icons.Default.Edit,
                contentDescription = if (stop.kind == RouteStop.Kind.LevelUp) "Not yet" else "Answer its questions",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * An island as a talk to rehearse aloud: the island's own sentences joined into about a minute of speech.
 * The meaning stays folded until asked for, so the talk is read (or listened to) in the language first.
 *
 * @param coastLanguage The language the talk is in, so a screen reader reads it in that language's voice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TalkSheet(state: TalkUiState, coastLanguage: String, onSpeak: () -> Unit, onAnother: () -> Unit, onDismiss: () -> Unit) {
    var showMeaning by rememberSaveable(state.talk) { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            // The whole sheet scrolls: with the meaning shown, a talk is longer than the screen.
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            Text(
                text = "${state.island.emoji ?: "🎙️"} ${state.island.name}, as a talk",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            val talk = state.talk
            // What comes of the wait is read out by a screen reader: the reason there is no talk, or that it is there.
            val arrival = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            when {
                state.error != null -> Text(state.error, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = arrival)
                talk == null -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
                    CircularProgressIndicator(color = Sunlit, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Joining your sentences into a talk…", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                else -> {
                    Text(
                        text = "Your own sentences from this island, joined into about a minute of speech. Read it aloud a few times, then say it without looking.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = arrival
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceRaised, MaterialTheme.shapes.medium)
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(inLanguage(talk.targetText, coastLanguage), style = MaterialTheme.typography.bodyLarge)
                        if (showMeaning) {
                            HorizontalDivider(color = TextSecondary.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 12.dp))
                            Text(talk.nativeText, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        if (state.canSpeak) {
                            TextButton(onClick = onSpeak, contentPadding = PaddingValues(horizontal = 4.dp)) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = SunlitText, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Listen", style = MaterialTheme.typography.labelMedium, color = SunlitText)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        TextButton(onClick = { showMeaning = !showMeaning }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                            Text(if (showMeaning) "Hide the meaning" else "Show the meaning", style = MaterialTheme.typography.labelMedium, color = SunlitText)
                        }
                    }
                }
            }
            if (!state.isWriting) {
                CoastButton(onClick = if (state.talk != null) onDismiss else onAnother, modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight)) {
                    Text(if (state.talk != null) "Done" else "Try again")
                }
                if (state.talk != null) {
                    TextButton(onClick = onAnother, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Write another version", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    }
                }
            }
        }
    }
}
