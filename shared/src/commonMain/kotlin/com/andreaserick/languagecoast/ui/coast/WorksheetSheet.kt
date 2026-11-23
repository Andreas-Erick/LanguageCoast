package com.andreaserick.languagecoast.ui.coast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.andreaserick.languagecoast.data.ProviderCapacity
import com.andreaserick.languagecoast.data.SheetKind
import com.andreaserick.languagecoast.data.BRIEF_CHIPS_MAX
import com.andreaserick.languagecoast.data.briefChips
import com.andreaserick.languagecoast.data.chipsInBrief
import com.andreaserick.languagecoast.data.toggleChip
import com.andreaserick.languagecoast.data.IslandSummary
import com.andreaserick.languagecoast.data.TranslationProvider
import com.andreaserick.languagecoast.data.islandEmoji
import com.andreaserick.languagecoast.ui.theme.OnSunlit
import com.andreaserick.languagecoast.data.WORKSHEET_COUNT_RANGE
import com.andreaserick.languagecoast.data.WORKSHEET_MIN_CARDS
import com.andreaserick.languagecoast.data.WorksheetBatch
import com.andreaserick.languagecoast.data.WorksheetDifficulty
import com.andreaserick.languagecoast.data.WorksheetDirection
import com.andreaserick.languagecoast.data.WorksheetOptions
import com.andreaserick.languagecoast.data.WorksheetPdf
import com.andreaserick.languagecoast.data.worksheetRequests
import com.andreaserick.languagecoast.ui.components.ButtonHeight
import com.andreaserick.languagecoast.ui.components.CoastButton
import com.andreaserick.languagecoast.ui.components.CoastSecondaryButton
import com.andreaserick.languagecoast.ui.components.CoastTextField
import com.andreaserick.languagecoast.ui.components.IconTile
import com.andreaserick.languagecoast.ui.components.SegmentedSwitch
import com.andreaserick.languagecoast.ui.components.coastChipBorder
import com.andreaserick.languagecoast.ui.components.coastChipColors
import com.andreaserick.languagecoast.ui.components.collapseExit
import com.andreaserick.languagecoast.ui.components.expandEnter
import com.andreaserick.languagecoast.ui.components.plural
import com.andreaserick.languagecoast.ui.theme.ChipContainer
import com.andreaserick.languagecoast.ui.theme.CoralText
import com.andreaserick.languagecoast.ui.theme.Seafoam
import com.andreaserick.languagecoast.ui.theme.SurfaceRaised
import com.andreaserick.languagecoast.ui.theme.SurfaceSelected
import com.andreaserick.languagecoast.ui.theme.Sunlit
import com.andreaserick.languagecoast.ui.theme.SunlitText
import com.andreaserick.languagecoast.ui.theme.TextPrimary
import com.andreaserick.languagecoast.ui.theme.TextSecondary
import com.andreaserick.languagecoast.ui.theme.tabular

/**
 * The coast's practice sheet, from picking what it should be like to holding the finished PDF: which way
 * to translate, how hard and how many sentences; then the wait while the AI provider writes them; then the
 * file, to download or open.
 *
 * @param islands The coast's islands, of which the user picks the ones the sentences are made from.
 * @param files Saves and opens the finished PDF; null leaves those buttons out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorksheetSheet(
    state: WorksheetUiState,
    coastLanguage: String,
    nativeLanguage: String,
    islands: List<IslandSummary>,
    provider: TranslationProvider,
    files: WorksheetFiles?,
    onOptionsChange: (WorksheetOptions) -> Unit,
    onCreate: () -> Unit,
    onCancel: () -> Unit,
    onStartNew: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // The number of sentences can be typed, and the button under it must stay in reach.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            val toLanguage = if (state.options.direction == WorksheetDirection.ToTarget) coastLanguage else nativeLanguage
            // The cards the sheet is made from: those of the islands picked for it.
            val cardCount = islands.filter { state.options.includes(it.island.islandId) }.sumOf { it.cardCount }
            when {
                state.pdf != null -> WorksheetReady(
                    pdf = state.pdf,
                    sentenceCount = state.sentenceCount,
                    askedFor = state.options.count,
                    toLanguage = toLanguage,
                    custom = state.options.kind == SheetKind.Custom,
                    provider = provider,
                    files = files,
                    onStartNew = onStartNew
                )
                state.isCreating -> WorksheetProgress(state = state, cardCount = cardCount, provider = provider, onCancel = onCancel)
                else -> WorksheetOptionsForm(
                    state = state,
                    coastLanguage = coastLanguage,
                    nativeLanguage = nativeLanguage,
                    islands = islands,
                    cardCount = cardCount,
                    provider = provider,
                    onOptionsChange = onOptionsChange,
                    onCreate = onCreate
                )
            }
        }
    }
}

/** What the sheet should be like, and the button that has it written. */
@Composable
private fun ColumnScope.WorksheetOptionsForm(
    state: WorksheetUiState,
    coastLanguage: String,
    nativeLanguage: String,
    islands: List<IslandSummary>,
    cardCount: Int,
    provider: TranslationProvider,
    onOptionsChange: (WorksheetOptions) -> Unit,
    onCreate: () -> Unit
) {
    val options = state.options
    val custom = options.kind == SheetKind.Custom
    val picked = islands.filter { options.includes(it.island.islandId) }
    val allPicked = picked.size == islands.size
    // What the number field says, which while typing can be no number or one outside the limits.
    var countText by rememberSaveable(options.count) { mutableStateOf("${options.count}") }
    var countBlurred by rememberSaveable(options.count) { mutableStateOf(false) }
    val countIsValid = countText.toIntOrNull() in WORKSHEET_COUNT_RANGE
    // Only show red error state after blur/done (never punish on first keystroke while replacing digits).
    val showCountError = !countIsValid && countBlurred
    // What stands in the way, if anything: said here, before a request is spent on finding out.
    val blocker = when {
        picked.isEmpty() -> "Pick at least one island to make the sheet from."
        cardCount < WORKSHEET_MIN_CARDS && !allPicked ->
            "The ${if (picked.size == 1) "island" else "islands"} you picked ${if (picked.size == 1) "has" else "have"} ${plural(cardCount, "card")}. A sheet needs at least $WORKSHEET_MIN_CARDS: pick more."
        cardCount < WORKSHEET_MIN_CARDS ->
            "A practice sheet is made from the coast's cards, and this coast has ${plural(cardCount, "card")}. Add at least $WORKSHEET_MIN_CARDS first."
        provider == TranslationProvider.OnDevice ->
            "On-device translation can't write ${if (custom) "exercises" else "sentences"}. Pick an AI provider in Settings to make a practice sheet."
        else -> null
    }
    val theCards = when {
        allPicked -> "this coast's ${plural(cardCount, "card")}"
        picked.isEmpty() -> "the cards on the islands you pick"
        else -> "the ${plural(cardCount, "card")} on the islands you picked"
    }

    Text("Practice sheet", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    SegmentedSwitch(
        options = SheetKind.entries.map { it.label },
        selectedIndex = options.kind.ordinal,
        onSelect = { onOptionsChange(options.copy(kind = SheetKind.entries[it])) },
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
    )
    Text(
        text = if (custom) {
            "Exercises on the grammar you name, written with words from $theCards, as a PDF to print or fill in. " +
                "The answers come on a page of their own."
        } else {
            "New sentences to translate, each built from one of $theCards, as a PDF to print or fill in. A sentence may bring a new word or two where it needs them; " +
                "with a cloud AI provider they are explained under it. The answers come on a page of their own."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary,
        modifier = Modifier.padding(top = 10.dp)
    )

    if (custom) {
        FieldLabel("What to practise")
        BriefField(brief = options.brief, coastLanguage = coastLanguage, onChange = { onOptionsChange(options.copy(brief = it)) })
        if (provider == TranslationProvider.LocalAi) {
            Text(
                text = "Small models write these less well.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }

    if (islands.size > 1) {
        IslandPicker(
            islands = islands,
            picked = picked.mapTo(HashSet()) { it.island.islandId },
            // Every island picked is stored as "all of them", so an island added later is in the next sheet too.
            onChange = { ids -> onOptionsChange(options.copy(islandIds = ids.takeIf { it.size < islands.size })) }
        )
    }

    // A custom sheet's instructions are in the learner's language and its exercises in the coast's: there is no way round to pick.
    if (!custom) {
        FieldLabel("Direction")
        DirectionSwitch(
            direction = options.direction,
            coastLanguage = coastLanguage,
            nativeLanguage = nativeLanguage,
            onChange = { onOptionsChange(options.copy(direction = it)) }
        )
    }

    FieldLabel("Difficulty")
    SegmentedSwitch(
        options = WorksheetDifficulty.entries.map { it.label },
        selectedIndex = options.difficulty.ordinal,
        onSelect = { onOptionsChange(options.copy(difficulty = WorksheetDifficulty.entries[it])) },
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        text = if (custom) options.difficulty.customHint else options.difficulty.hint,
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
        modifier = Modifier.padding(top = 6.dp)
    )

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(if (custom) "Exercises" else "Sentences", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Text(
                // A number outside the limits is said in words too, not by the colour alone.
                text = (if (showCountError) "Enter " else "") + "${WORKSHEET_COUNT_RANGE.first} to ${WORKSHEET_COUNT_RANGE.last}",
                style = MaterialTheme.typography.bodySmall,
                color = if (showCountError) CoralText else TextSecondary
            )
        }
        CountStepper(
            count = options.count,
            unit = if (custom) "exercise" else "sentence",
            text = countText,
            showError = showCountError,
            onTextChange = { typed ->
                countText = typed
                typed.toIntOrNull()?.takeIf { it in WORKSHEET_COUNT_RANGE }?.let {
                    countBlurred = false
                    onOptionsChange(options.copy(count = it))
                }
            },
            // Leaving the field with something else in it puts the number back.
            onDone = {
                countBlurred = true
                if (countText.toIntOrNull() !in WORKSHEET_COUNT_RANGE) {
                    countText = "${options.count}"
                    countBlurred = false
                }
            },
            onChange = { next ->
                countBlurred = false
                countText = "$next"
                onOptionsChange(options.copy(count = next))
            }
        )
    }

    val message = blocker ?: state.error
    if (message != null) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (blocker != null) TextSecondary else CoralText,
            // Why there is no sheet arrives after the wait, without a tap: a screen reader reads it out.
            modifier = Modifier.padding(top = 14.dp).semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
    CoastButton(
        onClick = onCreate,
        // A custom sheet is nothing without its brief.
        enabled = blocker == null && countIsValid && !(custom && options.brief.isBlank()),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = ButtonHeight)
    ) {
        Text(if (state.error != null && blocker == null) "Try again" else "Create PDF")
    }
    if (blocker == null) {
        Text(
            text = worksheetEstimate(options.count, provider, options.difficulty, options.kind),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
    }
}

/**
 * Which islands the sheet is made from. Folded, it says how many are in; unfolded, every island of the
 * coast with a tick to take it in or leave it out, and links for all and none.
 */
@Composable
private fun IslandPicker(islands: List<IslandSummary>, picked: Set<Int>, onChange: (Set<Int>) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val all = islands.mapTo(HashSet()) { it.island.islandId }
    FieldLabel("Islands")
    Column(modifier = Modifier.fillMaxWidth().background(ChipContainer, MaterialTheme.shapes.medium)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClickLabel = if (open) "Hide the islands" else "Choose islands", role = Role.Button) { open = !open }
                .padding(start = 16.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (picked.size) {
                        islands.size -> "All ${islands.size} islands"
                        0 -> "No island picked"
                        else -> "${picked.size} of ${islands.size} islands"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (picked.isEmpty()) CoralText else TextPrimary
                )
                if (picked.size in 1 until islands.size) {
                    Text(
                        text = islands.filter { it.island.islandId in picked }.joinToString(", ") { it.island.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = TextSecondary
            )
        }
        AnimatedVisibility(visible = open, enter = expandEnter(), exit = collapseExit()) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 8.dp)) {
                // Spaced apart, so that None isn't hit when All was meant.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 4.dp)) {
                    TextButton(onClick = { onChange(all) }, enabled = picked.size < islands.size, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("All", style = MaterialTheme.typography.labelMedium, color = if (picked.size < islands.size) SunlitText else TextSecondary)
                    }
                    TextButton(onClick = { onChange(emptySet()) }, enabled = picked.isNotEmpty(), contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("None", style = MaterialTheme.typography.labelMedium, color = if (picked.isNotEmpty()) SunlitText else TextSecondary)
                    }
                }
                islands.forEach { summary ->
                    val id = summary.island.islandId
                    val ticked = id in picked
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .toggleable(value = ticked, role = Role.Checkbox) { onChange(if (ticked) picked - id else picked + id) }
                            // 48dp high with the tick box: the rows touch, so each has to be a full touch target by itself.
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        Checkbox(
                            checked = ticked,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = Sunlit, checkmarkColor = OnSunlit, uncheckedColor = TextSecondary)
                        )
                        Text(
                            text = "${islandEmoji(summary.island)} ${summary.island.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)
                        )
                        Text(plural(summary.cardCount, "card"), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }
        }
    }
}

/**
 * The box for a custom sheet's brief, with the example chips for the coast's language under it. What is
 * typed is held here and passed on as it changes, so a keystroke never waits on the round trip through the
 * screen's state (the "About you" fields lost letters that way). A tapped chip changes the same draft, so
 * it shows in the box at once and stays editable there.
 */
@Composable
private fun BriefField(brief: String, coastLanguage: String, onChange: (String) -> Unit) {
    var draft by rememberSaveable { mutableStateOf(brief) }
    var typed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(brief) { if (!typed) draft = brief }
    fun change(text: String) {
        typed = true
        draft = text.take(BRIEF_LENGTH)
        onChange(draft)
    }
    CoastTextField(
        value = draft,
        onValueChange = ::change,
        placeholder = { Text("For example: the plural of nouns, or the dative after mit") },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        minLines = 2,
        modifier = Modifier.fillMaxWidth()
    )
    val groups = remember(coastLanguage) { briefChips(coastLanguage) }
    val offered = remember(groups) { groups.flatMap { it.chips } }
    val tapped = chipsInBrief(draft, offered)
    Text(
        text = if (tapped.size < BRIEF_CHIPS_MAX) "Or tap up to $BRIEF_CHIPS_MAX examples to add them." else "That's $BRIEF_CHIPS_MAX examples: tap one to take it out.",
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
    // Rows of other chips (such as the learner's recent briefs) go above these, in the same Column.
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        groups.forEach { group ->
            BriefChipRow(
                title = group.area.label,
                chips = group.chips,
                tapped = tapped,
                full = tapped.size >= BRIEF_CHIPS_MAX,
                onTap = { change(toggleChip(draft, it, offered)) }
            )
        }
    }
}

/**
 * One area of example chips, folded to its name and how many of its chips are in the brief; unfolded, the
 * chips themselves. A tapped chip shows as picked; once the brief holds [BRIEF_CHIPS_MAX], the others rest.
 *
 * @param tapped The chips the brief holds now, from every area.
 * @param full Whether the brief holds as many chips as it takes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BriefChipRow(title: String, chips: List<String>, tapped: List<String>, full: Boolean, onTap: (String) -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    val inBrief = chips.count { it in tapped }
    Column(modifier = Modifier.fillMaxWidth().background(ChipContainer, MaterialTheme.shapes.medium)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClickLabel = if (open) "Hide the $title examples" else "Show the $title examples", role = Role.Button) { open = !open }
                .heightIn(min = 48.dp)
                .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                text = if (inBrief > 0) "$inBrief in your brief" else plural(chips.size, "example"),
                style = MaterialTheme.typography.bodySmall,
                color = if (inBrief > 0) SunlitText else TextSecondary,
                modifier = Modifier.padding(end = 8.dp)
            )
            Icon(imageVector = if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = TextSecondary)
        }
        AnimatedVisibility(visible = open, enter = expandEnter(), exit = collapseExit()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
            ) {
                chips.forEach { chip ->
                    val picked = chip in tapped
                    FilterChip(
                        selected = picked,
                        onClick = { onTap(chip) },
                        enabled = picked || !full,
                        label = { Text(chip, style = MaterialTheme.typography.labelMedium) },
                        colors = coastChipColors(),
                        border = coastChipBorder(enabled = picked || !full, selected = picked),
                        shape = MaterialTheme.shapes.small
                    )
                }
            }
        }
    }
}

/** A brief can be this long: a sentence or two. The prompt quotes its first 300 characters. */
private const val BRIEF_LENGTH = 400

/** The small heading over one of the sheet's settings. */
@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
}

/**
 * Which way the sheet is translated, shown as "English → German" with what that means for the user under
 * it. Tapping swaps the two, as tapping the direction does while studying.
 */
@Composable
private fun DirectionSwitch(direction: WorksheetDirection, coastLanguage: String, nativeLanguage: String, onChange: (WorksheetDirection) -> Unit) {
    val toTarget = direction == WorksheetDirection.ToTarget
    val (from, to) = if (toTarget) nativeLanguage to coastLanguage else coastLanguage to nativeLanguage
    Surface(
        onClick = { onChange(if (toTarget) WorksheetDirection.ToNative else WorksheetDirection.ToTarget) },
        shape = MaterialTheme.shapes.medium,
        color = ChipContainer,
        contentColor = TextPrimary,
        modifier = Modifier
            .fillMaxWidth()
            // A screen reader says what a tap does by itself; the action only needs its name. The surface's own click stays.
            .semantics {
                contentDescription = "Translating from $from to $to"
                role = Role.Button
                onClick(label = "swap the languages", action = null)
            }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 60.dp).padding(start = 16.dp, end = 14.dp, top = 10.dp, bottom = 10.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("$from → $to", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "The sheet is in $from; you write the $to.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = TextSecondary, modifier = Modifier.padding(start = 10.dp))
        }
    }
}

/**
 * How long a sheet of [count] sentences will take with [provider], said before the button is tapped: a
 * model answers a batch of sentences in some seconds, a local one in most of a minute, and one that thinks
 * before it answers (Claude) in a minute or two.
 */
internal fun worksheetEstimate(
    count: Int,
    provider: TranslationProvider,
    difficulty: WorksheetDifficulty = WorksheetDifficulty.Medium,
    kind: SheetKind = SheetKind.Translation
): String {
    val what = if (kind == SheetKind.Custom) "exercises" else "sentences"
    val batch = WorksheetBatch.of(provider, difficulty)
    val requests = worksheetRequests(count, batch)
    val seconds = requests * batch.seconds
    val duration = when {
        seconds < 50 -> "less than a minute"
        seconds < 90 -> "about a minute"
        seconds < 3600 -> "about ${(seconds + 30) / 60} minutes"
        seconds < 5400 -> "about an hour"
        else -> "about ${(seconds + 1800) / 3600} hours"
    }
    return if (requests == 1) "${provider.label} writes the $what. It takes $duration."
    else "${provider.label} writes the $what a few at a time, in $requests requests. It takes $duration."
}

/**
 * How many sentences the sheet gets: a number that can be typed, between a minus and a plus that stop at
 * the ends of [WORKSHEET_COUNT_RANGE].
 *
 * @param count The number the sheet will have.
 * @param unit What is counted, for a screen reader: "sentence" or "exercise".
 * @param text What the field shows, which while typing may be no valid number ([isValid]).
 * @param onDone Called when the user is done typing: the keyboard's Done, or leaving the field.
 */
@Composable
private fun CountStepper(count: Int, unit: String, text: String, showError: Boolean, onTextChange: (String) -> Unit, onDone: () -> Unit, onChange: (Int) -> Unit) {
    val focusManager = LocalFocusManager.current
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    var wasFocused by remember { mutableStateOf(false) }
    // The field's own state holds the selection; its text follows [text] (the plus and minus change it too).
    var field by remember { mutableStateOf(TextFieldValue(text)) }
    LaunchedEffect(text) {
        if (field.text != text) field = TextFieldValue(text, TextRange(text.length))
    }
    LaunchedEffect(focused) {
        // Tapping the number selects it, so that typing replaces it instead of adding digits to it.
        if (focused) field = field.copy(selection = TextRange(0, field.text.length))
        if (wasFocused && !focused) onDone()
        wasFocused = focused
    }
    val shape = MaterialTheme.shapes.small
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton(Icons.Default.Remove, "One $unit fewer", enabled = count > WORKSHEET_COUNT_RANGE.first) { onChange(count - 1) }
        BasicTextField(
            value = field,
            onValueChange = { typed ->
                // Digits only, and no more of them than the largest number has.
                val digits = typed.text.filter { it.isDigit() }.take("${WORKSHEET_COUNT_RANGE.last}".length)
                field = if (digits == typed.text) typed else TextFieldValue(digits, TextRange(digits.length))
                if (digits != text) onTextChange(digits)
            },
            textStyle = MaterialTheme.typography.titleLarge.tabular.copy(color = TextPrimary, textAlign = TextAlign.Center),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            cursorBrush = SolidColor(Sunlit),
            interactionSource = interactions,
            modifier = Modifier
                .padding(horizontal = 6.dp)
                // A definite width: a text field without one takes room for ten letters. Wider with large text, so the
                // number isn't cut, but only as wide as three digits need: the label beside it needs the room too.
                .width((44.dp * LocalDensity.current.fontScale).coerceAtLeast(68.dp))
                .heightIn(min = 48.dp)
                .background(ChipContainer, shape)
                .then(
                    if (focused || showError) Modifier.border(
                        1.5.dp,
                        if (showError) CoralText else com.andreaserick.languagecoast.ui.theme.AccentControl,
                        shape
                    ) else Modifier
                )
                .semantics {
                    contentDescription = "Number of ${unit}s"
                    // What is wrong with the number, for a screen reader; sighted users get it from the text beside the field.
                    if (showError) this[SemanticsProperties.Error] = "Enter a number from ${WORKSHEET_COUNT_RANGE.first} to ${WORKSHEET_COUNT_RANGE.last}"
                },
            decorationBox = { field -> Box(contentAlignment = Alignment.Center) { field() } }
        )
        StepButton(Icons.Default.Add, "One $unit more", enabled = count < WORKSHEET_COUNT_RANGE.last) { onChange(count + 1) }
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = SurfaceSelected,
            contentColor = TextPrimary,
            disabledContainerColor = ChipContainer,
            disabledContentColor = TextSecondary.copy(alpha = 0.5f)
        )
    ) {
        Icon(icon, contentDescription = description)
    }
}

/** The wait while the AI provider writes the sentences, with a way out. */
@Composable
private fun ColumnScope.WorksheetProgress(state: WorksheetUiState, cardCount: Int, provider: TranslationProvider, onCancel: () -> Unit) {
    Text("Writing your practice sheet", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
        CircularProgressIndicator(color = Sunlit, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = if (state.written == 0 && (ProviderCapacity.of(provider).thinksFirst || state.options.difficulty == WorksheetDifficulty.Hard)) {
                "Asking ${provider.label} (planning longer ${if (state.options.kind == SheetKind.Custom) "exercises" else "sentences"} takes a moment)…"
            } else {
                "Asking ${provider.label}…"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
    val unit = if (state.options.kind == SheetKind.Custom) "exercise" else "sentence"
    Text(
        text = if (state.written > 0) "${state.written} of ${plural(state.options.count, unit)} written so far."
        else "${plural(state.options.count, unit)} from ${plural(cardCount, "card")}.",
        // Tabular figures, so the count doesn't jitter as it climbs.
        style = MaterialTheme.typography.bodyLarge.tabular,
        modifier = Modifier.padding(top = 12.dp)
    )
    if (state.written > 0) {
        LinearProgressIndicator(
            progress = { (state.written.toFloat() / state.options.count).coerceIn(0f, 1f) },
            color = Sunlit,
            trackColor = SurfaceRaised,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(6.dp)
        )
    } else {
        // The app's own bar rather than Material's: it holds still when the user has asked for reduced motion.
        WorkingBar(modifier = Modifier.padding(top = 12.dp))
    }
    Text(
        text = "It keeps going if you close this sheet, as long as you stay on this coast and keep the app open.",
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
        modifier = Modifier.padding(top = 6.dp)
    )
    CoastSecondaryButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(top = 20.dp).heightIn(min = ButtonHeight)) {
        Text("Cancel")
    }
}

/** The finished sheet: what is in it, and the buttons that download and open the PDF. */
@Composable
private fun ColumnScope.WorksheetReady(
    pdf: WorksheetPdf,
    sentenceCount: Int,
    askedFor: Int,
    toLanguage: String,
    custom: Boolean,
    provider: TranslationProvider,
    files: WorksheetFiles?,
    onStartNew: () -> Unit
) {
    var download by remember(pdf) { mutableStateOf<WorksheetDownload?>(null) }
    var isSaving by remember(pdf) { mutableStateOf(false) }
    var noViewer by remember(pdf) { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconTile(size = 48.dp) { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = SunlitText) }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Your practice sheet is ready",
                style = MaterialTheme.typography.titleLarge,
                // It arrives after the wait, without a tap: a screen reader reads it out.
                modifier = Modifier.semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                }
            )
            Text(pdf.fileName, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        }
    }
    Text(
        text = if (custom) "${plural(sentenceCount, "exercise")} on ${plural(pdf.pageCount, "page")}, with the answers at the end."
        else "${plural(sentenceCount, "sentence")} to translate into $toLanguage on ${plural(pdf.pageCount, "page")}, with the answers at the end.",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(top = 16.dp)
    )
    if (sentenceCount < askedFor) {
        Text(
            text = "${provider.label} wrote $sentenceCount of the $askedFor ${if (custom) "exercises" else "sentences"} you asked for.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
    Text(
        text = "Written by ${provider.label} · can make mistakes",
        style = MaterialTheme.typography.labelSmall,
        color = TextSecondary,
        modifier = Modifier.padding(top = 8.dp)
    )

    if (files != null) {
        val saved = download as? WorksheetDownload.Saved
        val buttonModifier = Modifier.fillMaxWidth().padding(top = 20.dp).heightIn(min = ButtonHeight)
        if (saved != null) {
            // In the button's place and as high as it, so nothing jumps.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = buttonModifier) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Seafoam, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(saved.place?.let { "Saved to $it" } ?: "Saved", style = MaterialTheme.typography.labelLarge)
            }
        } else {
            CoastButton(
                onClick = {
                    isSaving = true
                    files.download(pdf) { result ->
                        isSaving = false
                        download = result
                    }
                },
                enabled = !isSaving,
                modifier = buttonModifier
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Download PDF")
            }
        }
        CoastSecondaryButton(
            onClick = { noViewer = !files.open(pdf) },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = ButtonHeight)
        ) {
            Text("Open")
        }
        val problem = when {
            download == WorksheetDownload.Failed -> "The file couldn't be saved. Check that your device has storage left and try again."
            noViewer -> "No app on this device opens PDFs. Download the file to open it elsewhere."
            else -> null
        }
        if (problem != null) {
            Text(problem, style = MaterialTheme.typography.bodyMedium, color = CoralText, modifier = Modifier.padding(top = 12.dp))
        }
    }
    TextButton(onClick = onStartNew, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp)) {
        Text("Make another", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
    }
}
