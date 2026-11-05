package com.andreaserick.languagecoast.ui.study

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.andreaserick.languagecoast.data.AlternativeVerdict
import com.andreaserick.languagecoast.data.AnswerMatch
import com.andreaserick.languagecoast.data.CardVerdict
import com.andreaserick.languagecoast.data.Cloze
import com.andreaserick.languagecoast.data.Flashcard
import com.andreaserick.languagecoast.data.Grade
import com.andreaserick.languagecoast.data.checkAnswer
import com.andreaserick.languagecoast.ui.components.ButtonHeight
import com.andreaserick.languagecoast.ui.components.CoastButton
import com.andreaserick.languagecoast.ui.components.CoastSecondaryButton
import com.andreaserick.languagecoast.ui.components.CoastTextField
import com.andreaserick.languagecoast.ui.components.ContentCard
import com.andreaserick.languagecoast.ui.components.FeedbackKind
import com.andreaserick.languagecoast.ui.components.LocalFeedback
import com.andreaserick.languagecoast.ui.components.LocalUndoMessenger
import com.andreaserick.languagecoast.ui.components.ScreenMargin
import com.andreaserick.languagecoast.ui.components.SegmentedSwitch
import com.andreaserick.languagecoast.ui.theme.CoralText
import com.andreaserick.languagecoast.ui.theme.Seafoam
import com.andreaserick.languagecoast.ui.theme.Sunlit
import com.andreaserick.languagecoast.ui.theme.SurfaceRaised
import com.andreaserick.languagecoast.ui.theme.TextPrimary
import com.andreaserick.languagecoast.ui.theme.TextSecondary

/**
 * The switch between "Flip", "Type" and "Fill gap", next to which way cards are studied ("English → German").
 * Tapping the direction swaps it: from the native language trains recall, from the coast's language
 * trains recognition. Fill gap always blanks a word of the translation, so it has no direction.
 */
@Composable
internal fun StudyModeRow(
    mode: StudyMode,
    onModeChange: (StudyMode) -> Unit,
    nativeLanguage: String,
    targetLanguage: String,
    isReversed: Boolean,
    onReversedChange: (Boolean) -> Unit
) {
    val (from, to) = if (isReversed) targetLanguage to nativeLanguage else nativeLanguage to targetLanguage
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ScreenMargin, end = ScreenMargin, top = 8.dp)
    ) {
        SegmentedSwitch(
            options = listOf("Flip", "Type", "Fill gap"),
            selectedIndex = mode.ordinal,
            onSelect = { onModeChange(StudyMode.entries[it]) },
            modifier = Modifier.fillMaxWidth()
        )
        if (mode == StudyMode.Cloze) {
            Text(
                text = "Type the missing word",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
            )
        } else {
            TextButton(
                onClick = { onReversedChange(!isReversed) },
                colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary),
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .semantics { contentDescription = "Studying $from to $to. Tap to swap." }
            ) {
                Text("$from → $to", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.padding(start = 6.dp).size(18.dp))
            }
        }
    }
}

/**
 * The "fill the gap" study view: the translation with its key word blanked and the native sentence as
 * the hint. The typed word is checked leniently (see [checkAnswer]): right or nearly right can be graded,
 * a slip counts as Hard, wrong is Again.
 */
@Composable
fun ClozeStudyView(
    currentCard: Flashcard,
    cloze: Cloze,
    intervals: Map<Grade, Int>,
    onGrade: (Grade) -> Unit,
    onSpeak: (() -> Unit)? = null
) {
    var userInput by remember(currentCard.cardId) { mutableStateOf("") }
    var match by remember(currentCard.cardId) { mutableStateOf<AnswerMatch?>(null) }
    val feedback = LocalFeedback.current
    val result = remember { BringIntoViewRequester() }
    LaunchedEffect(match) {
        if (match != null) result.bringIntoView()
    }

    fun check() {
        if (userInput.isBlank()) return
        val outcome = checkAnswer(userInput, listOf(cloze.word))
        match = outcome
        feedback.play(if (outcome == AnswerMatch.Wrong) FeedbackKind.Failure else FeedbackKind.Success)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        ContentCard(padding = PaddingValues(start = 20.dp, top = 16.dp, end = 8.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 40.dp)) {
                Text("Fill the gap", style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.weight(1f))
                if (match != null && onSpeak != null) {
                    IconButton(onClick = onSpeak) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Read aloud", tint = Sunlit)
                    }
                }
            }
            Text(
                text = if (match == null) cloze.display else cloze.before + cloze.word + cloze.after,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(end = 12.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(currentCard.nativeText, style = MaterialTheme.typography.titleMedium, color = TextSecondary, modifier = Modifier.padding(end = 12.dp))
        }

        CoastTextField(
            value = userInput,
            onValueChange = {
                userInput = it
                match = null
            },
            label = { Text("The missing word...") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { check() }),
            modifier = Modifier.fillMaxWidth()
        )

        when (val outcome = match) {
            null -> CoastButton(
                onClick = ::check,
                enabled = userInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight)
            ) {
                Text("Check")
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().bringIntoViewRequester(result)) {
                AnswerVerdict(outcome = outcome, expected = cloze.word)
                when (outcome) {
                    AnswerMatch.Exact -> GradeButtons(intervals = intervals, onGrade = onGrade)
                    is AnswerMatch.Almost -> HardContinue(onGrade = onGrade)
                    AnswerMatch.Wrong -> AgainContinue(onGrade = onGrade)
                }
            }
        }
    }
}

/** "Perfect!", "Almost!" or "Not quite", with the expected answer when the typed one wasn't it. */
@Composable
internal fun AnswerVerdict(outcome: AnswerMatch, expected: String, alsoCorrect: List<String> = emptyList(), onSpeak: (() -> Unit)? = null) {
    ContentCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (outcome == AnswerMatch.Wrong) Icons.Default.Cancel else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = when (outcome) {
                    AnswerMatch.Exact -> Seafoam
                    is AnswerMatch.Almost -> Sunlit
                    AnswerMatch.Wrong -> CoralText
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when (outcome) {
                    AnswerMatch.Exact -> "Perfect!"
                    is AnswerMatch.Almost -> "Almost!"
                    AnswerMatch.Wrong -> "Not quite"
                },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
        }
        if (outcome is AnswerMatch.Almost) {
            Text("You typed", style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.padding(top = 12.dp))
            Text(outcome.typed, style = MaterialTheme.typography.titleMedium, color = TextSecondary)
        }
        if (outcome != AnswerMatch.Exact) {
            Text("Correct answer", style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.padding(top = 12.dp))
            Text(expected, style = MaterialTheme.typography.titleLarge)
        }
        if (alsoCorrect.isNotEmpty()) {
            Text(
                text = "Also correct: ${alsoCorrect.joinToString(" · ")}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (onSpeak != null) {
            TextButton(
                onClick = onSpeak,
                colors = ButtonDefaults.textButtonColors(contentColor = Sunlit),
                contentPadding = PaddingValues(horizontal = 0.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text("Listen", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** A slip of a letter counts as remembered, but only just: the card is graded Hard. */
@Composable
internal fun HardContinue(onGrade: (Grade) -> Unit) {
    CoastButton(onClick = { onGrade(Grade.Hard) }, modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight)) {
        Text("Continue")
    }
    Text(
        text = "A small slip counts as Hard: the card comes back a little sooner.",
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

/** A wrong answer means the card wasn't remembered, so there is nothing to grade: it's "Again". */
@Composable
internal fun AgainContinue(onGrade: (Grade) -> Unit) {
    CoastButton(onClick = { onGrade(Grade.Again) }, modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight)) {
        Text("Continue")
    }
    Text(
        text = "This card comes back later in this session.",
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * A bottom sheet explaining a word on the current card: the AI provider's explanation (or why there
 * is none), and a link to dict.cc when it has a dictionary for the language.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WordSheet(explanation: WordExplanation, dictionaryUrl: String?, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val undoMessenger = LocalUndoMessenger.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            Text(explanation.word, style = MaterialTheme.typography.headlineMedium)
            Text(explanation.sentence, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
            Spacer(modifier = Modifier.height(16.dp))
            when {
                explanation.isLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Sunlit, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Asking ${explanation.providerLabel}…", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                explanation.text != null -> Text(explanation.text, style = MaterialTheme.typography.bodyLarge)
                else -> Text(explanation.error.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = CoralText)
            }
            if (explanation.text != null) {
                Text(
                    text = "From ${explanation.providerLabel} · can make mistakes",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            if (dictionaryUrl != null) {
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = {
                        runCatching { uriHandler.openUri(dictionaryUrl) }
                            .onFailure { undoMessenger.showMessage("No browser found to open dict.cc") }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary),
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    Text("Look up on dict.cc", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold)
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.padding(start = 6.dp).size(16.dp))
                }
            }
        }
    }
}

/**
 * A bottom sheet with the AI provider's check of a card: what the translation says word for word, and that
 * it is fine, or what it should say and why, with a button to make that the card's translation. Under it
 * the user can ask about a translation of their own ("is this correct as well?") and keep it as an
 * alternative. Nothing changes unless a button is tapped, as the provider can be wrong too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CardCheckSheet(
    check: CardCheck,
    onApply: () -> Unit,
    onAskAlternative: (String) -> Unit,
    onSaveAlternative: () -> Unit,
    onDismiss: () -> Unit
) {
    // Opens in full: it ends in a text field, which a half-open sheet would hide behind the keyboard.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            Text(check.targetText, style = MaterialTheme.typography.headlineMedium)
            Text(check.nativeText, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
            Spacer(modifier = Modifier.height(16.dp))
            // What the translation says, for the user to hold against what the card is meant to say.
            check.readsAs?.let { reading ->
                Text("Word for word, it says", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Text(reading, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 2.dp, bottom = 16.dp))
            }
            when (val verdict = check.verdict) {
                null -> if (check.isLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = Sunlit, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Asking ${check.providerLabel}…", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                } else {
                    Text(check.error.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = CoralText)
                }
                CardVerdict.Fine -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Seafoam)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Nothing found", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        // The reading is the user's own check on the verdict, which a small model gets wrong.
                        text = if (check.readsAs == null) "${check.providerLabel} found nothing wrong with it."
                        else "${check.providerLabel} found nothing wrong with it. If the reading above isn't what you meant, edit the card.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                is CardVerdict.Better -> {
                    Text(
                        text = if (verdict.currentIsWrong) "It should be" else "People would rather say",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Text(verdict.better, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 2.dp))
                    if (verdict.reason.isNotBlank()) {
                        Text(verdict.reason, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 10.dp))
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    CoastButton(onClick = onApply, modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight)) {
                        Text("Use this")
                    }
                    Text(
                        text = if (verdict.currentIsWrong) "It replaces the card's translation." else "The card's translation stays as an alternative.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
            }
            if (check.verdict != null) {
                Text(
                    text = "From ${check.providerLabel} · can make mistakes",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            // Once the check is through, so the two answers don't arrive on top of each other.
            if (check.canAskAlternative && !check.isLoading) {
                AlternativeQuestion(
                    check = check,
                    // One accent per sheet: "Use this" has it when the check suggests a change.
                    accentSave = check.verdict !is CardVerdict.Better,
                    onAsk = onAskAlternative,
                    onSave = onSaveAlternative
                )
            }
        }
    }
}

/**
 * The end of [CardCheckSheet]: a field for the user's own way of saying what the card says, a button that
 * asks the AI provider whether it is correct as well, and the answer with a button to keep the sentence as
 * an alternative on the card.
 */
@Composable
private fun AlternativeQuestion(check: CardCheck, accentSave: Boolean, onAsk: (String) -> Unit, onSave: () -> Unit) {
    var text by rememberSaveable(check.cardId) { mutableStateOf("") }
    val asked = check.alternative
    val focusManager = LocalFocusManager.current
    val answer = remember { BringIntoViewRequester() }
    // The answer appears under the field, which the keyboard may be covering.
    LaunchedEffect(asked?.isLoading, asked?.saved) {
        if (asked != null) answer.bringIntoView()
    }

    fun ask() {
        if (text.isBlank() || asked?.isLoading == true) return
        // The keyboard has done its part; the answer needs the room.
        focusManager.clearFocus()
        onAsk(text)
    }

    HorizontalDivider(color = SurfaceRaised, modifier = Modifier.padding(top = 20.dp, bottom = 16.dp))
    Text("Correct as well?", style = MaterialTheme.typography.titleMedium)
    Text(
        text = "Type another way to say it in ${check.targetLanguage}, and ${check.providerLabel} says whether it works too.",
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary,
        modifier = Modifier.padding(top = 2.dp)
    )
    CoastTextField(
        value = text,
        // A sentence, not a paragraph: Enter asks.
        onValueChange = { text = it.replace("\n", " ") },
        label = { Text("Your ${check.targetLanguage}") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { ask() }),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
    )
    CoastSecondaryButton(
        onClick = ::ask,
        enabled = text.isNotBlank() && asked?.isLoading != true,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = ButtonHeight)
    ) {
        Text("Ask ${check.providerLabel}", maxLines = 1, overflow = TextOverflow.Ellipsis)
    }

    if (asked == null) return
    Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp).bringIntoViewRequester(answer)) {
        val verdict = asked.verdict
        when {
            asked.isLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Sunlit, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Asking ${check.providerLabel}…", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            verdict == null -> Text(asked.error.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = CoralText)
            verdict == AlternativeVerdict.OnTheCard -> Text("The card has that one already.", style = MaterialTheme.typography.bodyLarge)
            verdict is AlternativeVerdict.Correct -> {
                AlternativeHeadline(correct = true, providerLabel = check.providerLabel, asked = asked.text)
                verdict.note?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                }
                SaveAlternative(label = "Save as an alternative", saved = asked.saved != null, accent = accentSave, onSave = onSave)
            }
            verdict is AlternativeVerdict.Incorrect -> {
                AlternativeHeadline(correct = false, providerLabel = check.providerLabel, asked = asked.text)
                if (verdict.reason.isNotBlank()) {
                    Text(verdict.reason, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                }
                verdict.corrected?.let { corrected ->
                    Text("It would be right as", style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.padding(top = 14.dp))
                    Text(corrected, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 2.dp))
                    SaveAlternative(label = "Save the corrected one", saved = asked.saved != null, accent = accentSave, onSave = onSave)
                }
            }
        }
        if (verdict is AlternativeVerdict.Correct || verdict is AlternativeVerdict.Incorrect) {
            Text(
                text = "From ${check.providerLabel} · can make mistakes",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

/**
 * What the provider made of the sentence, over the sentence that was asked about (the field may say something
 * else by now). Worded as the provider's finding, not as a fact: a small model agrees with almost anything.
 */
@Composable
private fun AlternativeHeadline(correct: Boolean, providerLabel: String, asked: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (correct) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (correct) Seafoam else CoralText
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (correct) "$providerLabel finds it correct" else "$providerLabel finds it wrong",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f)
        )
    }
    Text(asked, style = MaterialTheme.typography.titleMedium, color = TextSecondary, modifier = Modifier.padding(top = 6.dp))
}

/**
 * The button that keeps a sentence as an alternative on the card, and in its place, once tapped, the line
 * that says it was kept. Both are as high as a button, so the sheet doesn't jump.
 */
@Composable
private fun SaveAlternative(label: String, saved: Boolean, accent: Boolean, onSave: () -> Unit) {
    val modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = ButtonHeight)
    when {
        saved -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = modifier) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Seafoam, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Saved as an alternative on this card", style = MaterialTheme.typography.labelMedium)
        }
        accent -> CoastButton(onClick = onSave, modifier = modifier) { Text(label) }
        else -> CoastSecondaryButton(onClick = onSave, modifier = modifier) { Text(label) }
    }
    if (!saved) {
        Text(
            text = "It then counts as correct when you type it, and shows on the back of the card.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
    }
}

/** The headline of the session-complete screen for [scope]. */
internal fun completionTitle(scope: StudyScope): String = when (scope) {
    StudyScope.Island -> "Island explored!"
    StudyScope.Coast -> "Coast explored!"
    StudyScope.Today -> "Today's review done!"
    StudyScope.Leaky -> "Leaks plugged!"
    StudyScope.Daily -> "Done for today!"
}

/** What the "all caught up" screen says for [scope] when nothing is due. */
internal fun caughtUpBody(scope: StudyScope, nextDueInDays: Int?): String = when {
    scope == StudyScope.Leaky -> "No card has slipped away twice. Nothing is leaking right now."
    scope == StudyScope.Daily && nextDueInDays == null -> "Nothing is due and no new sentences are ready yet. Add one of your own, or come back tomorrow."
    nextDueInDays == null -> "No cards are due right now."
    nextDueInDays == 1 -> "The next cards are due tomorrow."
    else -> "The next cards are due in $nextDueInDays days."
}

/** The label of the button that leaves a session for [scope]. */
internal fun backLabel(scope: StudyScope): String = when (scope) {
    StudyScope.Island -> "Back to islands"
    StudyScope.Coast -> "Back to coast"
    StudyScope.Today, StudyScope.Leaky -> "Back to coasts"
    StudyScope.Daily -> "Back to Today"
}
