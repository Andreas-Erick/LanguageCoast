package com.andreaserick.languagecoast.ui.coast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andreaserick.languagecoast.data.ProviderCapacity
import com.andreaserick.languagecoast.data.ImportProgress
import com.andreaserick.languagecoast.data.PROFILE_PLACEHOLDERS
import com.andreaserick.languagecoast.data.PhraseBank
import com.andreaserick.languagecoast.data.TranslationProvider
import com.andreaserick.languagecoast.data.profileExample
import com.andreaserick.languagecoast.data.profileLabel
import com.andreaserick.languagecoast.ui.components.ButtonHeight
import com.andreaserick.languagecoast.ui.components.CoastButton
import com.andreaserick.languagecoast.ui.components.CoastTextField
import com.andreaserick.languagecoast.ui.components.ContentCard
import com.andreaserick.languagecoast.ui.components.IconTile
import com.andreaserick.languagecoast.ui.components.plural
import com.andreaserick.languagecoast.ui.theme.CoralText
import com.andreaserick.languagecoast.ui.theme.Seafoam
import com.andreaserick.languagecoast.ui.theme.SurfaceRaised
import com.andreaserick.languagecoast.ui.theme.Sunlit
import com.andreaserick.languagecoast.ui.theme.SunlitText
import com.andreaserick.languagecoast.ui.theme.TextSecondary

/**
 * A starter topic the coast doesn't have yet.
 *
 * @property sentenceCount Sentences a whole-topic import can use (those with a blank per sentence are left to the sheet on Today).
 * @property profileBlanks The person's blanks the topic uses (name, city, country, job), to ask for once.
 */
data class StarterTopicOption(
    val theme: PhraseBank.Theme,
    val sentenceCount: Int,
    val profileBlanks: List<String>
)

/**
 * Confirms adding [options] (one topic, or several) to the coast: what it will do, the person's
 * blanks the topics use (started from [profile], the values from Settings, and only asked for when missing),
 * how long it will take with [provider], and the button.
 *
 * @param problem Why [provider] can't translate right now (no key yet, or not allowed to be sent text), or null when it can.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StarterTopicSheet(
    options: List<StarterTopicOption>,
    coastLanguage: String,
    provider: TranslationProvider,
    problem: String?,
    profile: Map<String, String>,
    onAdd: (fillers: Map<String, String>) -> Unit,
    onDismiss: () -> Unit
) {
    // What is typed into the blanks is kept through a rotation, as blank and value in turn.
    val fillers = rememberSaveable(
        saver = listSaver<SnapshotStateMap<String, String>, String>(
            save = { typed -> typed.flatMap { listOf(it.key, it.value) } },
            restore = { saved -> mutableStateMapOf<String, String>().apply { saved.chunked(2).forEach { put(it[0], it[1]) } } }
        )
    ) { mutableStateMapOf<String, String>().apply { putAll(profile) } }
    val blanks = PROFILE_PLACEHOLDERS.filter { blank -> options.any { blank in it.profileBlanks } }
    val missing = blanks.filter { profile[it].isNullOrBlank() }
    val language = mapOf(PhraseBank.LANGUAGE_PLACEHOLDER to coastLanguage)
    // Sentences whose blanks are filled (or that have none) count; the rest are left out.
    val filled = fillers.filterValues { it.isNotBlank() } + language
    val count = options.sumOf { option -> option.theme.sentences.count { PhraseBank.fill(it, filled) != null } }
    val perSentence = options.sumOf { option -> option.theme.sentences.count { PhraseBank.placeholders(it).any { p -> p != PhraseBank.LANGUAGE_PLACEHOLDER && p !in PROFILE_PLACEHOLDERS } } }
    val single = options.singleOrNull()
    val translatorName = if (provider == TranslationProvider.OnDevice) "the on-device translator" else provider.label

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            Text(
                text = if (single != null) "Add ${single.theme.emoji} ${single.theme.category}" else "Add ${options.size} starter topics",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = if (single != null) {
                    "${plural(single.sentenceCount, "sentence")} translated into $coastLanguage by $translatorName and filed on an island named ${single.theme.category}. They start as new cards, so today's session takes them a few at a time."
                } else {
                    "${options.size} topics, ${plural(options.sumOf { it.sentenceCount }, "sentence")}, translated into $coastLanguage by $translatorName, each topic on its own island. That is a lot of new cards at once; today's session still takes them a few at a time, so the rest will wait their turn."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            if (blanks.isNotEmpty()) {
                Text(
                    text = when {
                        missing.isEmpty() -> "Some sentences use your own details, filled in from About you in Settings. Change them here for this topic only."
                        missing.size < blanks.size -> "Some sentences use your own details. The ones from About you in Settings are filled in; add the rest if you like. A sentence with a blank you leave empty is left out."
                        else -> "Some sentences use your own details. Fill in what applies, or do it once under About you in Settings; a sentence with a blank you leave empty is left out."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                blanks.forEach { blank ->
                    CoastTextField(
                        value = fillers[blank].orEmpty(),
                        onValueChange = { fillers[blank] = it },
                        label = { Text(profileLabel(blank)) },
                        placeholder = { Text(profileExample(blank)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (perSentence > 0) {
                Text(
                    text = "${plural(perSentence, "sentence")} with a blank like [number] or [time] stay in the Starter sentences list on Today, to fill in one by one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            if (problem != null) {
                // Said here, before the button, so that nothing is started that can't be finished.
                Text(problem, style = MaterialTheme.typography.bodyMedium, color = CoralText)
            } else {
                Text(
                    text = "About ${importEstimate(count, provider)}. You can leave this screen while it runs, but keep the app open.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            CoastButton(
                onClick = { onAdd(fillers.filterValues { it.isNotBlank() }) },
                enabled = count > 0 && problem == null,
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight)
            ) {
                Text("Add ${plural(count, "card")}")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Not now", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** A rough duration for translating [count] sentences with [provider]: a model takes a batch at a time, the device one each. */
internal fun importEstimate(count: Int, provider: TranslationProvider): String {
    val seconds = ProviderCapacity.of(provider).starterSeconds(count)
    return when {
        seconds < 50 -> "a minute"
        seconds < 3600 -> plural(((seconds + 59) / 60).toInt(), "minute")
        else -> plural(((seconds + 3599) / 3600).toInt(), "hour")
    }
}

/**
 * How adding starter topics is going, at the top of the coast: the topic's tile, a bar and a way to stop
 * while it runs, and once it is done what was added and one button to clear it. A run that added nothing
 * gets no tick and no "added": it says that nothing came of it, and why.
 */
@Composable
internal fun ImportProgressCard(progress: ImportProgress, onStop: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val theme = PhraseBank.theme(progress.topic)
    val several = progress.topicCount > 1
    // Stopped by the user; a run that ended without a reason before its last sentence was, too.
    val stoppedEarly = progress.isFinished && progress.error == null && (progress.stopped || progress.done < progress.total)
    val nothingAdded = progress.isFinished && progress.error == null && progress.added == 0
    ContentCard(modifier = modifier, padding = PaddingValues(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            IconTile {
                when {
                    progress.error != null -> Icon(Icons.Default.Error, contentDescription = null, tint = CoralText)
                    // Sentences that all failed are a failure, and a run that added nothing didn't succeed at anything: no tick for either.
                    nothingAdded && progress.failed > 0 -> Icon(Icons.Default.Error, contentDescription = null, tint = CoralText)
                    nothingAdded -> Icon(Icons.Default.Close, contentDescription = null, tint = TextSecondary)
                    progress.isFinished -> Icon(Icons.Default.Check, contentDescription = null, tint = Seafoam)
                    else -> Text(theme?.emoji ?: "📚", fontSize = 22.sp)
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
                    nothingAdded -> {
                        Text(
                            text = when {
                                progress.failed > 0 -> "Nothing could be translated"
                                stoppedEarly -> "Stopped"
                                else -> "Nothing to add"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = when {
                                progress.failed > 0 ->
                                    "${plural(progress.failed, "sentence")} couldn't be translated. Check your connection and Settings › Translation, then add the ${if (several) "topics" else "topic"} again."
                                stoppedEarly -> "Nothing was added yet."
                                else -> "Every sentence of ${if (several) "these topics" else "this topic"} is on your coast already."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    progress.isFinished -> {
                        Text(
                            text = when {
                                stoppedEarly -> "Stopped"
                                several -> "${plural(progress.topicCount, "topic")} added"
                                else -> "${theme?.emoji?.let { "$it " } ?: ""}${progress.topic} added"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = when {
                                stoppedEarly -> "${plural(progress.added, "card")} added before you stopped. Add the ${if (several) "topics" else "topic"} again for the rest."
                                several -> "${plural(progress.added, "card")}, each topic on its own island."
                                else -> "${plural(progress.added, "card")} on the ${progress.topic} island, ready for today's session."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        if (progress.failed > 0) {
                            Text(
                                text = "${progress.failed} couldn't be translated; they're still in the Starter sentences list on Today.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    else -> {
                        Text("Adding ${progress.topic}…", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { if (progress.total == 0) 0f else progress.done.toFloat() / progress.total },
                            color = Sunlit,
                            trackColor = SurfaceRaised,
                            strokeCap = StrokeCap.Round,
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                            modifier = Modifier.fillMaxWidth().height(6.dp)
                        )
                        Text(
                            // The run only goes on while the app is on screen.
                            text = (if (several) "${progress.done} of ${progress.total} translated, ${plural(progress.topicCount, "topic")} in all."
                            else "${progress.done} of ${progress.total} translated.") + " Keep the app open until it is done.",
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
                TextButton(onClick = onDismiss) {
                    Text(if (progress.error != null || nothingAdded) "OK" else "Done", style = MaterialTheme.typography.labelMedium, color = SunlitText)
                }
            }
        }
    }
}
