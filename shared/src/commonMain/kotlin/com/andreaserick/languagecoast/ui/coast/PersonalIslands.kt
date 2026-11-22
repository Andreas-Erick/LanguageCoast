package com.andreaserick.languagecoast.ui.coast

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andreaserick.languagecoast.data.ProviderCapacity
import com.andreaserick.languagecoast.data.BuildProgress
import com.andreaserick.languagecoast.data.Flashcard
import com.andreaserick.languagecoast.data.IslandOrder
import com.andreaserick.languagecoast.data.Level
import com.andreaserick.languagecoast.data.PERSONAL_ISLAND_SENTENCES
import com.andreaserick.languagecoast.data.PersonalIsland
import com.andreaserick.languagecoast.data.PersonalIslandBuilder
import com.andreaserick.languagecoast.data.TranslationProvider
import com.andreaserick.languagecoast.ui.components.ButtonHeight
import com.andreaserick.languagecoast.ui.components.CoastButton
import com.andreaserick.languagecoast.ui.components.ContentCard
import com.andreaserick.languagecoast.ui.components.IconTile
import com.andreaserick.languagecoast.ui.components.inLanguage
import com.andreaserick.languagecoast.ui.components.plural
import com.andreaserick.languagecoast.ui.theme.CoralText
import com.andreaserick.languagecoast.ui.theme.OnSunlit
import com.andreaserick.languagecoast.ui.theme.Seafoam
import com.andreaserick.languagecoast.ui.theme.SurfaceRaised
import com.andreaserick.languagecoast.ui.theme.Sunlit
import com.andreaserick.languagecoast.ui.theme.SunlitText
import com.andreaserick.languagecoast.ui.theme.TextPrimary
import com.andreaserick.languagecoast.ui.theme.TextSecondary

/** Where an island about the user stands on a coast. */
enum class PersonalIslandState {
    /** None of its questions is answered yet. */
    Locked,
    /** Answered and not on the coast. */
    Ready,
    /** On the coast, but the answers have changed since it was written. */
    Changed
}

/**
 * An island the coast can have written, or written again.
 *
 * @property facts What it would be written from, as label and value.
 * @property raiseTo The level to take an island that is on the coast up to; null to write it at its own.
 */
data class PersonalIslandOption(
    val island: PersonalIsland,
    val state: PersonalIslandState,
    val facts: List<Pair<String, String>> = emptyList(),
    val raiseTo: Level? = null
) {
    val order: IslandOrder get() = IslandOrder(island.id, raiseTo)
}

/**
 * Confirms writing [options] (one island, or several) on the coast: what will happen, what one island is
 * written from, where the answers go with [provider], how long it takes, and the button.
 *
 * @param problem Why [provider] can't write right now (no key yet, or not allowed to be sent text), or null when it can.
 * @param onAboutYou Opens the questions at the island with this id, to change the answers first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PersonalIslandSheet(
    options: List<PersonalIslandOption>,
    coastLanguage: String,
    nativeLanguage: String,
    provider: TranslationProvider,
    problem: String?,
    onWrite: () -> Unit,
    onAboutYou: (islandId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val single = options.singleOrNull()
    val canWrite = provider != TranslationProvider.OnDevice
    val rewrites = options.count { it.state == PersonalIslandState.Changed && it.raiseTo == null }
    val aboutUser = options.any { it.island.isAboutUser }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            Text(
                text = when {
                    single == null -> "Write ${options.size} islands"
                    single.raiseTo != null -> "Take ${single.island.emoji} ${single.island.name} up to ${single.raiseTo.label}"
                    single.state == PersonalIslandState.Changed -> "Write ${single.island.emoji} ${single.island.name} again"
                    else -> "Write ${single.island.emoji} ${single.island.name}"
                },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = if (!canWrite) {
                    PersonalIslandBuilder.NEEDS_MODEL_MESSAGE + " On-device translation can only translate; it can't write sentences."
                } else {
                    val each = if (single != null) "" else " for each"
                    val from = when {
                        single?.raiseTo != null -> "from the same answers, with more to them: reasons, feelings, what came before and after. The sentences you have stay"
                        single != null && !single.island.isAboutUser -> "on this theme"
                        options.none { it.island.isAboutUser } -> "on its theme"
                    else -> "from your answers"
                    }
                    "${provider.label} writes up to $PERSONAL_ISLAND_SENTENCES sentences$each $from, in $nativeLanguage and $coastLanguage, and then reads them once more the way a native speaker would. " +
                        "They wait on the island as new cards and reach you a few a day, at your pace."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            if (rewrites > 0) {
                Text(
                    text = if (single != null) "Your answers changed since this island was written. Cards on it that you haven't studied yet are replaced; the ones you have studied stay, and you can look them over afterwards."
                    else "${plural(rewrites, "island")} will be written again because your answers changed. Cards you haven't studied yet are replaced; the ones you have studied stay.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            if (single != null && single.facts.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceRaised, MaterialTheme.shapes.small)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text("Written from", style = MaterialTheme.typography.labelMedium, color = SunlitText)
                    single.facts.forEach { (label, value) ->
                        Column {
                            Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            } else if (single == null) {
                Text(
                    text = options.joinToString(" · ") { "${it.island.emoji} ${it.island.name}" + (it.raiseTo?.let { level -> " (${level.label})" } ?: "") },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (canWrite && problem != null) {
                // Said here, before the button, so that nothing is started that can't be finished.
                Text(problem, style = MaterialTheme.typography.bodyMedium, color = CoralText)
            } else if (canWrite) {
                val sent = when {
                    !provider.isCloud -> "Nothing is sent to an AI provider. "
                    aboutUser -> "Your answers for ${if (single != null) "this island" else "these islands"} are sent to ${provider.label}. "
                    else -> ""
                }
                Text(
                    text = sent + "About ${buildEstimate(options.size, provider)}. You can leave this screen while it runs, but keep the app open. Look the sentences over when it is done.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            CoastButton(
                onClick = onWrite,
                enabled = canWrite && problem == null,
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight)
            ) {
                Text(if (single != null) "Write the island" else "Write ${options.size} islands")
            }
            if (aboutUser) {
                TextButton(onClick = { onAboutYou(single?.island?.id.orEmpty()) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Change my answers first", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** A rough duration for writing [islands] islands with [provider]: a cloud model takes two prompts each, a model on the phone several slow ones. */
internal fun buildEstimate(islands: Int, provider: TranslationProvider): String {
    val seconds = islands * ProviderCapacity.of(provider).secondsPerIsland
    return when {
        seconds < 50 -> "a minute"
        seconds < 3600 -> plural((seconds + 59) / 60, "minute")
        else -> plural((seconds + 3599) / 3600, "hour")
    }
}

/**
 * How writing islands is going, at the top of the coast: the island's tile, a bar and a way to stop while
 * it runs, and once it is done what was written, with a way to look the sentences over.
 *
 * @param onReview Opens the written cards to look over; null when there are none.
 */
@Composable
internal fun BuildProgressCard(
    progress: BuildProgress,
    onStop: () -> Unit,
    onReview: (() -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val several = progress.total > 1
    ContentCard(modifier = modifier, padding = PaddingValues(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            IconTile {
                when {
                    progress.error != null -> Icon(Icons.Default.Error, contentDescription = null, tint = CoralText)
                    // A run stopped before it wrote anything didn't succeed at anything: no tick for it.
                    progress.isFinished && progress.stopped && progress.added == 0 -> Icon(Icons.Default.Close, contentDescription = null, tint = TextSecondary)
                    progress.isFinished -> Icon(Icons.Default.Check, contentDescription = null, tint = Seafoam)
                    else -> Text(progress.emoji, fontSize = 22.sp)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
                    // A screen reader says how it ended when that arrives; the steps before it would only interrupt.
                    .then(if (progress.isFinished) Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite } else Modifier)
            ) {
                when {
                    progress.error != null -> {
                        Text("Couldn't finish", style = MaterialTheme.typography.titleMedium)
                        Text(progress.error, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
                    }
                    progress.isFinished -> {
                        val written = progress.done - progress.empty.size
                        Text(
                            text = when {
                                written == 0 -> if (progress.stopped) "Stopped" else "Nothing to write"
                                several -> "${plural(written, "island")} written"
                                else -> "${progress.emoji} ${progress.island} written"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (written > 0) {
                            Text(
                                text = "${plural(progress.added, "new card")}. They reach you a few a day, at your pace." +
                                    if (progress.stopped) " You stopped before the rest." else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        } else if (progress.stopped) {
                            Text("Nothing was written yet.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
                        }
                        if (progress.empty.isNotEmpty()) {
                            Text(
                                text = "The AI provider wrote nothing usable for ${progress.empty.joinToString(", ")}. Add a little more to those answers and try again.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    else -> {
                        Text("Writing ${progress.island}…", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (several) {
                            LinearProgressIndicator(
                                progress = { progress.done.toFloat() / progress.total },
                                color = Sunlit,
                                trackColor = SurfaceRaised,
                                strokeCap = StrokeCap.Round,
                                gapSize = 0.dp,
                                drawStopIndicator = {},
                                modifier = Modifier.fillMaxWidth().height(6.dp)
                            )
                        } else {
                            // How many sentences the model will write isn't known, so the bar only shows that it is at work.
                            WorkingBar()
                        }
                        Text(
                            // The run only goes on while the app is on screen.
                            text = (if (several) "Island ${progress.done + 1} of ${progress.total}, ${plural(progress.added, "card")} so far."
                            else "Up to $PERSONAL_ISLAND_SENTENCES sentences, in both languages.") + " Keep the app open until it is done.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            if (!progress.isFinished) {
                TextButton(onClick = onStop) { Text("Stop", style = MaterialTheme.typography.labelMedium, color = TextSecondary) }
            } else {
                if (onReview != null) {
                    TextButton(onClick = onReview) { Text("Look them over", style = MaterialTheme.typography.labelMedium, color = SunlitText) }
                }
                TextButton(onClick = onDismiss) {
                    Text(if (progress.error != null) "OK" else "Done", style = MaterialTheme.typography.labelMedium, color = if (onReview != null) TextSecondary else SunlitText)
                }
            }
        }
    }
}

/** A bar with a piece gliding across it: something is at work, and how far it is can't be told. */
@Composable
private fun WorkingBar(modifier: Modifier = Modifier) {
    val reducedMotion = com.andreaserick.languagecoast.ui.components.LocalReducedMotion.current
    val position = if (reducedMotion) {
        0.5f
    } else {
        val transition = rememberInfiniteTransition()
        transition.animateFloat(initialValue = 0f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing))).value
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            // Tells a screen reader that work is going on, as Material's own bar would.
            .progressSemantics()
            .clip(RoundedCornerShape(50))
            .background(SurfaceRaised)
            .drawBehind {
                val piece = size.width * 0.3f
                drawRoundRect(
                    color = Sunlit,
                    topLeft = Offset((size.width + piece) * position - piece, 0f),
                    size = Size(piece, size.height),
                    cornerRadius = CornerRadius(size.height / 2)
                )
            }
    )
}

/**
 * The cards a run of the builder left, for the user to look over: every sentence about their life is
 * ticked, and one that isn't true or isn't how they'd say it is unticked and removed. After an island was
 * written again, the studied cards that stayed are listed too, since the answers behind them may have changed.
 *
 * @param coastLanguage The language the cards' translations are in, so a screen reader reads them in its voice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BuildReviewSheet(review: BuildReview, coastLanguage: String, onRemove: (Set<Int>) -> Unit, onDismiss: () -> Unit) {
    // The ids of the cards unticked so far, kept through a rotation.
    val unticked = rememberSaveable(
        saver = listSaver<SnapshotStateList<Int>, Int>(save = { it.toList() }, restore = { it.toMutableStateList() })
    ) { mutableStateListOf<Int>() }

    @Composable
    fun CardRow(card: Flashcard) {
        val ticked = card.cardId !in unticked
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                // The row is the tick box: a screen reader says whether it is ticked, and reads the sentence as its name.
                .toggleable(value = ticked, role = Role.Checkbox) { if (it) unticked -= card.cardId else unticked += card.cardId }
                .padding(vertical = 6.dp)
        ) {
            Checkbox(
                checked = ticked,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = Sunlit, checkmarkColor = OnSunlit, uncheckedColor = TextSecondary),
                modifier = Modifier.padding(top = 2.dp, end = 12.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(inLanguage(card.targetText, coastLanguage), style = MaterialTheme.typography.bodyLarge, color = if (ticked) TextPrimary else TextSecondary)
                Text(card.nativeText, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            Text("Look them over", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(
                text = "You'll learn these by heart, and you are the one who knows if they're right. Untick anything that isn't true or isn't how you'd say it.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )
            // A level's worth of islands is a couple of hundred sentences: only the rows in view are drawn.
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(review.added) { CardRow(it) }
                if (review.kept.isNotEmpty()) {
                    item {
                        Text("Kept from before", style = MaterialTheme.typography.labelMedium, color = SunlitText, modifier = Modifier.padding(top = 16.dp))
                        Text(
                            text = "You've studied these, so they stayed. Untick the ones your new answers made untrue.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                        )
                    }
                    items(review.kept) { CardRow(it) }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            CoastButton(
                onClick = {
                    if (unticked.isNotEmpty()) onRemove(unticked.toSet())
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight)
            ) {
                Text(if (unticked.isEmpty()) "They're all right" else "Remove ${plural(unticked.size, "card")}, keep the rest")
            }
        }
    }
}
