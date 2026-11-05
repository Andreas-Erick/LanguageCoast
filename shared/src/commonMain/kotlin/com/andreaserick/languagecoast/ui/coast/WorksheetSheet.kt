package com.andreaserick.languagecoast.ui.coast

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.andreaserick.languagecoast.data.TranslationProvider
import com.andreaserick.languagecoast.data.WORKSHEET_COUNT_RANGE
import com.andreaserick.languagecoast.data.WORKSHEET_MIN_CARDS
import com.andreaserick.languagecoast.data.WorksheetDifficulty
import com.andreaserick.languagecoast.data.WorksheetDirection
import com.andreaserick.languagecoast.data.WorksheetOptions
import com.andreaserick.languagecoast.data.WorksheetPdf
import com.andreaserick.languagecoast.data.worksheetRequests
import com.andreaserick.languagecoast.ui.components.ButtonHeight
import com.andreaserick.languagecoast.ui.components.CoastButton
import com.andreaserick.languagecoast.ui.components.CoastSecondaryButton
import com.andreaserick.languagecoast.ui.components.IconTile
import com.andreaserick.languagecoast.ui.components.SegmentedSwitch
import com.andreaserick.languagecoast.ui.components.plural
import com.andreaserick.languagecoast.ui.theme.ChipContainer
import com.andreaserick.languagecoast.ui.theme.CoralText
import com.andreaserick.languagecoast.ui.theme.Seafoam
import com.andreaserick.languagecoast.ui.theme.SurfaceRaised
import com.andreaserick.languagecoast.ui.theme.SurfaceSelected
import com.andreaserick.languagecoast.ui.theme.Sunlit
import com.andreaserick.languagecoast.ui.theme.TextPrimary
import com.andreaserick.languagecoast.ui.theme.TextSecondary

/**
 * The coast's practice sheet, from picking what it should be like to holding the finished PDF: which way
 * to translate, how hard and how many sentences; then the wait while the AI provider writes them; then the
 * file, to download or open.
 *
 * @param cardCount Cards on the coast, which the sentences are made from.
 * @param files Saves and opens the finished PDF; null leaves those buttons out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorksheetSheet(
    state: WorksheetUiState,
    coastLanguage: String,
    nativeLanguage: String,
    cardCount: Int,
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
            when {
                state.pdf != null -> WorksheetReady(
                    pdf = state.pdf,
                    sentenceCount = state.sentenceCount,
                    askedFor = state.options.count,
                    toLanguage = toLanguage,
                    provider = provider,
                    files = files,
                    onStartNew = onStartNew
                )
                state.isCreating -> WorksheetProgress(state = state, cardCount = cardCount, provider = provider, onCancel = onCancel)
                else -> WorksheetOptionsForm(
                    state = state,
                    coastLanguage = coastLanguage,
                    nativeLanguage = nativeLanguage,
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
    cardCount: Int,
    provider: TranslationProvider,
    onOptionsChange: (WorksheetOptions) -> Unit,
    onCreate: () -> Unit
) {
    val options = state.options
    // What the number field says, which while typing can be no number or one outside the limits.
    var countText by remember(options.count) { mutableStateOf("${options.count}") }
    val countIsValid = countText.toIntOrNull() in WORKSHEET_COUNT_RANGE
    // What stands in the way, if anything: said here, before a request is spent on finding out.
    val blocker = when {
        cardCount < WORKSHEET_MIN_CARDS ->
            "A practice sheet is made from the coast's cards, and this coast has ${plural(cardCount, "card")}. Add at least $WORKSHEET_MIN_CARDS first."
        provider == TranslationProvider.OnDevice -> "On-device translation can't write sentences. Pick an AI provider in Settings to make a practice sheet."
        else -> null
    }

    Text("Practice sheet", style = MaterialTheme.typography.titleLarge)
    Text(
        text = "New sentences to translate, built from the words on this coast's ${plural(cardCount, "card")}, one island's topic at a time, " +
            "as a PDF to print or fill in. A sentence may bring one new word where it needs it. The answers come on a page of their own.",
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary,
        modifier = Modifier.padding(top = 4.dp)
    )

    FieldLabel("Direction")
    DirectionSwitch(
        direction = options.direction,
        coastLanguage = coastLanguage,
        nativeLanguage = nativeLanguage,
        onChange = { onOptionsChange(options.copy(direction = it)) }
    )

    FieldLabel("Difficulty")
    SegmentedSwitch(
        options = WorksheetDifficulty.entries.map { it.label },
        selectedIndex = options.difficulty.ordinal,
        onSelect = { onOptionsChange(options.copy(difficulty = WorksheetDifficulty.entries[it])) },
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        text = options.difficulty.hint,
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
        modifier = Modifier.padding(top = 6.dp)
    )

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Sentences", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Text(
                text = "${WORKSHEET_COUNT_RANGE.first} to ${WORKSHEET_COUNT_RANGE.last}",
                style = MaterialTheme.typography.bodySmall,
                color = if (countIsValid) TextSecondary else CoralText
            )
        }
        CountStepper(
            count = options.count,
            text = countText,
            isValid = countIsValid,
            onTextChange = { typed ->
                countText = typed
                typed.toIntOrNull()?.takeIf { it in WORKSHEET_COUNT_RANGE }?.let { onOptionsChange(options.copy(count = it)) }
            },
            // Leaving the field with something else in it puts the number back.
            onDone = { countText = "${options.count}" },
            onChange = { onOptionsChange(options.copy(count = it)) }
        )
    }

    val message = blocker ?: state.error
    if (message != null) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (blocker != null) TextSecondary else CoralText,
            modifier = Modifier.padding(top = 14.dp)
        )
    }
    CoastButton(
        onClick = onCreate,
        enabled = blocker == null && countIsValid,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = ButtonHeight)
    ) {
        Text(if (state.error != null && blocker == null) "Try again" else "Create PDF")
    }
    if (blocker == null) {
        Text(
            text = worksheetEstimate(options.count, provider),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
    }
}

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
            .semantics { contentDescription = "Translating from $from to $to. Tap to swap." }
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
 * model answers a batch of sentences in some seconds, a local one in most of a minute.
 */
internal fun worksheetEstimate(count: Int, provider: TranslationProvider): String {
    val isLocal = provider == TranslationProvider.LocalAi
    val requests = worksheetRequests(count, isLocal)
    val seconds = requests * if (isLocal) 40 else 10
    val duration = when {
        seconds < 50 -> "less than a minute"
        seconds < 90 -> "about a minute"
        seconds < 3600 -> "about ${(seconds + 30) / 60} minutes"
        seconds < 5400 -> "about an hour"
        else -> "about ${(seconds + 1800) / 3600} hours"
    }
    return if (requests == 1) "${provider.label} writes the sentences. It takes $duration."
    else "${provider.label} writes the sentences a few at a time, in $requests requests. It takes $duration."
}

/**
 * How many sentences the sheet gets: a number that can be typed, between a minus and a plus that stop at
 * the ends of [WORKSHEET_COUNT_RANGE].
 *
 * @param count The number the sheet will have.
 * @param text What the field shows, which while typing may be no valid number ([isValid]).
 * @param onDone Called when the user is done typing: the keyboard's Done, or leaving the field.
 */
@Composable
private fun CountStepper(count: Int, text: String, isValid: Boolean, onTextChange: (String) -> Unit, onDone: () -> Unit, onChange: (Int) -> Unit) {
    val focusManager = LocalFocusManager.current
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    var wasFocused by remember { mutableStateOf(false) }
    // The field's own state holds the selection; its text follows [text] (the plus and minus change it too).
    var field by remember { mutableStateOf(TextFieldValue(text)) }
    if (field.text != text) field = TextFieldValue(text, TextRange(text.length))
    LaunchedEffect(focused) {
        // Tapping the number selects it, so that typing replaces it instead of adding digits to it.
        if (focused) field = field.copy(selection = TextRange(0, field.text.length))
        if (wasFocused && !focused) onDone()
        wasFocused = focused
    }
    val shape = MaterialTheme.shapes.small
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton(Icons.Default.Remove, "One sentence fewer", enabled = count > WORKSHEET_COUNT_RANGE.first) { onChange(count - 1) }
        BasicTextField(
            value = field,
            onValueChange = { typed ->
                // Digits only, and no more of them than the largest number has.
                val digits = typed.text.filter { it.isDigit() }.take("${WORKSHEET_COUNT_RANGE.last}".length)
                field = if (digits == typed.text) typed else TextFieldValue(digits, TextRange(digits.length))
                if (digits != text) onTextChange(digits)
            },
            textStyle = MaterialTheme.typography.titleLarge.copy(color = TextPrimary, textAlign = TextAlign.Center),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            cursorBrush = SolidColor(Sunlit),
            interactionSource = interactions,
            modifier = Modifier
                .padding(horizontal = 6.dp)
                // Wide enough for three digits, so the buttons don't shift between 99 and 100.
                .width(68.dp)
                .height(44.dp)
                .background(ChipContainer, shape)
                .then(if (focused || !isValid) Modifier.border(1.5.dp, if (isValid) Sunlit else CoralText, shape) else Modifier)
                .semantics { contentDescription = "Number of sentences" },
            decorationBox = { field -> Box(contentAlignment = Alignment.Center) { field() } }
        )
        StepButton(Icons.Default.Add, "One sentence more", enabled = count < WORKSHEET_COUNT_RANGE.last) { onChange(count + 1) }
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
    Text("Writing your practice sheet", style = MaterialTheme.typography.titleLarge)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
        CircularProgressIndicator(color = Sunlit, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text("Asking ${provider.label}…", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
    Text(
        text = if (state.written > 0) "${state.written} of ${plural(state.options.count, "sentence")} written so far."
        else "${plural(state.options.count, "sentence")} from the ${plural(cardCount, "card")} on this coast.",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(top = 12.dp)
    )
    // A sheet that takes several requests shows how far along it is.
    if (worksheetRequests(state.options.count, provider == TranslationProvider.LocalAi) > 1) {
        LinearProgressIndicator(
            progress = { state.written.toFloat() / state.options.count },
            color = Sunlit,
            trackColor = SurfaceRaised,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(6.dp)
        )
    }
    Text(
        text = "It keeps going if you close this sheet, as long as you stay on this coast.",
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
    provider: TranslationProvider,
    files: WorksheetFiles?,
    onStartNew: () -> Unit
) {
    var download by remember(pdf) { mutableStateOf<WorksheetDownload?>(null) }
    var isSaving by remember(pdf) { mutableStateOf(false) }
    var noViewer by remember(pdf) { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconTile(size = 48.dp) { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Sunlit) }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Your practice sheet is ready", style = MaterialTheme.typography.titleLarge)
            Text(pdf.fileName, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        }
    }
    Text(
        text = "${plural(sentenceCount, "sentence")} to translate into $toLanguage on ${plural(pdf.pageCount, "page")}, the last one with the answers.",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(top = 16.dp)
    )
    if (sentenceCount < askedFor) {
        Text(
            text = "${provider.label} wrote $sentenceCount of the $askedFor sentences you asked for.",
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
            download == WorksheetDownload.Failed -> "The file couldn't be saved. Check that the phone has storage left and try again."
            noViewer -> "No app on this phone opens PDFs. Download the file to open it elsewhere."
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
