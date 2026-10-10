package com.andreaserick.languagecoast.ui.study

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andreaserick.languagecoast.data.Flashcard
import com.andreaserick.languagecoast.data.Grade
import com.andreaserick.languagecoast.data.isSameSentence
import com.andreaserick.languagecoast.ui.components.CardOptionsMenu
import com.andreaserick.languagecoast.ui.components.EditCardSheet
import com.andreaserick.languagecoast.ui.components.LocalUndoMessenger
import com.andreaserick.languagecoast.ui.components.ScreenHeader
import com.andreaserick.languagecoast.ui.components.plural
import com.andreaserick.languagecoast.ui.theme.CardTints
import com.andreaserick.languagecoast.ui.theme.CardWash
import com.andreaserick.languagecoast.ui.theme.MistWhite
import com.andreaserick.languagecoast.ui.theme.CoralAccent
import com.andreaserick.languagecoast.ui.theme.CoralText
import com.andreaserick.languagecoast.ui.theme.DeepOceanBlue
import com.andreaserick.languagecoast.ui.theme.SandBeige
import com.andreaserick.languagecoast.ui.theme.SandMuted
import com.andreaserick.languagecoast.ui.theme.WaveTeal
import com.andreaserick.languagecoast.util.dictCcSearchUrl
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The main screen for studying flashcards within a specific "Language Island" (category).
 * Provides two modes of study: "Flip Cards" (standard flashcard) and "Active Type" (writing practice).
 * The island is taken from the navigation arguments by [StudyViewModel].
 *
 * @param onNavigateBack Callback to navigate back to the previous screen.
 * @param onShowCards Called to open the list of the island's cards.
 */
@Composable
fun StudyScreen(onNavigateBack: () -> Unit, onShowCards: () -> Unit, viewModel: StudyViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentCard = uiState.currentCard
    val undoMessenger = LocalUndoMessenger.current
    var editingCard by remember { mutableStateOf<Flashcard?>(null) }

    editingCard?.let { card ->
        EditCardSheet(
            card = card,
            islands = uiState.coastIslands,
            nativeLanguage = uiState.nativeLanguage,
            targetLanguage = uiState.targetLanguage,
            onSave = { edit ->
                viewModel.editCard(card.cardId, edit)
                editingCard = null
            },
            onDismiss = { editingCard = null }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = uiState.islandName,
            onBack = onNavigateBack,
            actions = {
                if (uiState.hasCards) {
                    IconButton(onClick = onShowCards) {
                        Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = "All cards", tint = SandBeige)
                    }
                }
                if (currentCard != null && !uiState.isSessionComplete) {
                    CardOptionsMenu(
                        onEdit = { editingCard = currentCard },
                        onDelete = {
                            viewModel.deleteCurrentCard { deleted ->
                                undoMessenger.show("Card deleted") { viewModel.restore(deleted) }
                            }
                        }
                    )
                }
            }
        )

        when {
            uiState.isLoading -> Unit
            !uiState.hasCards -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("🌊", fontSize = 56.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text("No flashcards here yet!", color = SandBeige)
            }
            uiState.isCaughtUp -> CaughtUpView(
                nextDueInDays = uiState.nextDueInDays,
                onPracticeAll = viewModel::practiceAll,
                onBack = onNavigateBack
            )
            uiState.isSessionComplete -> SessionCompleteView(
                reviewedCount = uiState.totalCards,
                againCount = uiState.againCount,
                streakCount = uiState.streakCount,
                onPracticeAll = viewModel::practiceAll,
                onBack = onNavigateBack
            )
            currentCard != null -> {
                StudyModeTabs(isTypingMode = uiState.isTypingMode, onModeChange = viewModel::setTypingMode)
                DirectionToggle(
                    nativeLanguage = uiState.nativeLanguage,
                    targetLanguage = uiState.targetLanguage,
                    isReversed = uiState.isReversed,
                    onReversedChange = viewModel::setReversed
                )
                SessionProgress(uiState)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // key(cardId) resets per-card state (flip state, typed answer)
                    // when we move to a different card.
                    key(currentCard.cardId) {
                        if (uiState.isTypingMode) {
                            TypeStudyView(
                                currentCard = currentCard,
                                nativeLang = uiState.nativeLanguage,
                                isReversed = uiState.isReversed,
                                intervals = uiState.gradeIntervals,
                                onGrade = viewModel::onGrade,
                                onSpeak = viewModel::speakAnswer.takeIf { uiState.canSpeak }
                            )
                        } else {
                            FlipStudyView(
                                currentCard = currentCard,
                                nativeLang = uiState.nativeLanguage,
                                targetLang = uiState.targetLanguage,
                                isReversed = uiState.isReversed,
                                intervals = uiState.gradeIntervals,
                                onGrade = viewModel::onGrade,
                                onSpeak = viewModel::speakAnswer.takeIf { uiState.canSpeak },
                                onSpeakText = viewModel::speak.takeIf { uiState.canSpeak },
                                onMakeMain = viewModel::makeMainTranslation
                            )
                        }
                    }
                }

                // Browsing is a secondary action, so it stays small and out of the way.
                if (uiState.sessionCards.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = viewModel::previous, enabled = uiState.canGoBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                            Text("Previous")
                        }
                        TextButton(onClick = viewModel::next, enabled = uiState.canGoForward) {
                            Text("Skip")
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shows which way cards are studied ("English → German") and swaps it when tapped: from the native
 * language trains recall, from the coast's language trains recognition.
 */
@Composable
private fun DirectionToggle(
    nativeLanguage: String,
    targetLanguage: String,
    isReversed: Boolean,
    onReversedChange: (Boolean) -> Unit
) {
    val (from, to) = if (isReversed) targetLanguage to nativeLanguage else nativeLanguage to targetLanguage
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
        TextButton(
            onClick = { onReversedChange(!isReversed) },
            colors = ButtonDefaults.textButtonColors(contentColor = SandMuted),
            modifier = Modifier.semantics { contentDescription = "Studying $from to $to. Tap to swap." }
        ) {
            Text("$from → $to", fontSize = 14.sp)
            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/** Tabs to switch between flipping cards to reveal the answer and typing the answer ("Active Type"). */
@Composable
private fun StudyModeTabs(isTypingMode: Boolean, onModeChange: (Boolean) -> Unit) {
    PrimaryTabRow(
        selectedTabIndex = if (isTypingMode) 1 else 0,
        containerColor = Color.Transparent,
        contentColor = SandBeige,
        divider = {}
    ) {
        Tab(
            selected = !isTypingMode,
            onClick = { onModeChange(false) },
            text = { Text("Flip Cards", fontWeight = FontWeight.SemiBold) },
            selectedContentColor = SandBeige,
            unselectedContentColor = SandMuted
        )
        Tab(
            selected = isTypingMode,
            onClick = { onModeChange(true) },
            text = { Text("Active Type", fontWeight = FontWeight.SemiBold) },
            selectedContentColor = SandBeige,
            unselectedContentColor = SandMuted
        )
    }
}

/** A progress bar for the session plus how many cards are left. */
@Composable
private fun SessionProgress(uiState: StudyUiState) {
    val progress by animateFloatAsState(uiState.progress, label = "progress")
    Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp)) {
        LinearProgressIndicator(
            progress = { progress },
            color = SandBeige,
            trackColor = SandBeige.copy(alpha = 0.2f),
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        )
        Text(
            text = "${plural(uiState.sessionCards.size, "card")} left",
            color = SandMuted,
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 6.dp)
        )
    }
}

/** How far (in dp) a card must be dragged to count as an Again/Good swipe. */
private const val SWIPE_THRESHOLD_DP = 110

/**
 * A flashcard that flips when tapped, revealing the translation. Once flipped, it can be graded with
 * the grade buttons or by swiping it left (Again) or right (Good).
 * Words on the back are underlined and open dict.cc when dict.cc has a dictionary for the language.
 *
 * @param currentCard The [Flashcard] data to display.
 * @param nativeLang The user's native language name.
 * @param targetLang The language being studied.
 * @param intervals Days until the card is due again after each grade, shown on the buttons.
 * @param onGrade Callback when the card is graded.
 * @param onSpeak Reads the translation aloud; null hides the speaker button (no voice for the language).
 * @param onSpeakText Reads an alternative aloud; null hides those speaker buttons.
 * @param onMakeMain Makes an alternative the card's translation.
 */
@Composable
fun FlipStudyView(
    currentCard: Flashcard,
    nativeLang: String,
    targetLang: String,
    intervals: Map<Grade, Int>,
    onGrade: (Grade) -> Unit,
    isReversed: Boolean = false,
    onSpeak: (() -> Unit)? = null,
    onSpeakText: ((String) -> Unit)? = null,
    onMakeMain: (String) -> Unit = {}
) {
    // Keyed on the card's ID, not the card: changing its translation must not flip it back.
    var isFlipped by remember(currentCard.cardId) { mutableStateOf(false) }
    var showAlternatives by remember(currentCard.cardId) { mutableStateOf(false) }
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offsetX = remember(currentCard.cardId) { Animatable(0f) }
    val thresholdPx = with(LocalDensity.current) { SWIPE_THRESHOLD_DP.dp.toPx() }
    // Reversed, the translation is the question, so looking its words up would give the answer away.
    val hasDictionary = !isReversed && dictCcSearchUrl("", nativeLang, targetLang) != null

    // Again re-queues the card, so with one card left the same card comes back: reset it explicitly.
    fun grade(grade: Grade) {
        haptics.performHapticFeedback(if (grade == Grade.Again) HapticFeedbackType.Reject else HapticFeedbackType.Confirm)
        scope.launch {
            offsetX.snapTo(0f)
            isFlipped = false
            onGrade(grade)
        }
    }

    // Animate the card rotation
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "flip"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f, fill = false)) {
            // Shown behind the card while it is dragged, to tell which way grades it how.
            if (isFlipped && offsetX.value != 0f) {
                SwipeLabel(offset = offsetX.value, thresholdPx = thresholdPx)
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    // Shrink the card on short screens so the buttons below keep their full height.
                    .heightIn(min = 160.dp, max = 260.dp)
                    // Must come before the rotated graphicsLayer: inside it, the flipped (180°) card
                    // would mirror drag directions and a swipe right would count as "Again".
                    .pointerInput(isFlipped) {
                        if (!isFlipped) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                when {
                                    offsetX.value > thresholdPx -> grade(Grade.Good)
                                    offsetX.value < -thresholdPx -> grade(Grade.Again)
                                    else -> scope.launch { offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                                }
                            },
                            onDragCancel = { scope.launch { offsetX.animateTo(0f) } },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                            }
                        )
                    }
                    .graphicsLayer {
                        rotationY = rotation
                        cameraDistance = 12f * density
                        translationX = offsetX.value
                        rotationZ = offsetX.value / 40f
                    }
                    .clickable { isFlipped = !isFlipped },
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                // Light card faces with dark text keep both sides readable
                colors = CardDefaults.cardColors(
                    // Sky on the front keeps the small "Tap to flip" hint above 4.5:1 contrast.
                    containerColor = if (isFlipped) SandBeige else CardTints.first(),
                    contentColor = DeepOceanBlue
                )
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    // Keep content upright regardless of the card's rotation
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationY = if (isFlipped) 180f else 0f },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isReversed) {
                            // The translation is the question: it can be heard before flipping.
                            CardFaceText(if (isFlipped) currentCard.nativeText else currentCard.targetText)
                            if (!isFlipped) {
                                if (onSpeak != null) {
                                    IconButton(onClick = onSpeak, modifier = Modifier.align(Alignment.TopEnd)) {
                                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Read aloud", tint = DeepOceanBlue)
                                    }
                                }
                                FlipHint()
                            }
                        } else if (isFlipped) {
                            ClickableWordSentence(
                                sentence = currentCard.targetText,
                                onWordClick = if (hasDictionary) { word ->
                                    dictCcSearchUrl(word, nativeLang, targetLang)?.let { url ->
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                                        } catch (e: ActivityNotFoundException) {
                                            Toast.makeText(context, "No browser found to open dict.cc", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else null
                            )
                            if (onSpeak != null) {
                                IconButton(onClick = onSpeak, modifier = Modifier.align(Alignment.TopEnd)) {
                                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Read aloud", tint = DeepOceanBlue)
                                }
                            }
                        } else {
                            CardFaceText(currentCard.nativeText)
                            FlipHint()
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = when {
                !isFlipped -> " "
                hasDictionary -> "Tap a word to look it up · Swipe ← Again, Good →"
                else -> "Swipe ← Again, Good →"
            },
            fontSize = 13.sp,
            color = SandMuted,
            textAlign = TextAlign.Center
        )
        // Alternatives are other ways to say the translation, which only make sense when it is the answer.
        if (isFlipped && !isReversed && currentCard.alternatives.isNotEmpty()) {
            TextButton(onClick = { showAlternatives = true }, colors = ButtonDefaults.textButtonColors(contentColor = SandBeige)) {
                Text(plural(currentCard.alternatives.size, "alternative"), fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.ExpandLess, contentDescription = null)
            }
        }
        if (showAlternatives) {
            AlternativesSheet(
                card = currentCard,
                onSpeak = onSpeakText,
                onMakeMain = onMakeMain,
                onDismiss = { showAlternatives = false }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (isFlipped) {
            GradeButtons(intervals = intervals, onGrade = ::grade)
        } else {
            Button(
                onClick = { isFlipped = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WaveTeal, contentColor = Color.White)
            ) {
                Text("Show answer", fontSize = 18.sp)
            }
        }
    }
}

/** The sentence on a face of the flip card that has no tappable words. */
@Composable
private fun CardFaceText(text: String) {
    Text(
        text = text,
        fontSize = 24.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(16.dp)
    )
}

/** "Tap to flip" at the bottom of the front of the flip card. */
@Composable
private fun BoxScope.FlipHint() {
    Text(
        text = "Tap to flip",
        fontSize = 13.sp,
        color = DeepOceanBlue.copy(alpha = 0.85f),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 12.dp)
    )
}

/** "Again" / "Good" while a card is dragged, fading in as the drag nears the threshold. */
@Composable
private fun SwipeLabel(offset: Float, thresholdPx: Float) {
    val good = offset > 0
    Text(
        text = if (good) "Good" else "Again",
        color = if (good) SandBeige else CoralText,
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .graphicsLayer { alpha = (abs(offset) / thresholdPx).coerceIn(0f, 1f) },
        textAlign = if (good) TextAlign.Start else TextAlign.End
    )
}

/**
 * The four grading buttons shared by both study modes, each showing when the card would come back.
 * "Good" is the usual choice, so it gets the strongest color.
 */
@Composable
private fun GradeButtons(intervals: Map<Grade, Int>, onGrade: (Grade) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Grade.entries.forEach { grade ->
            val (container, content) = when (grade) {
                // Dark text: white on coral is only 3:1 contrast.
                Grade.Again -> CoralAccent to DeepOceanBlue
                Grade.Hard -> CardWash to SandBeige
                Grade.Good -> SandBeige to DeepOceanBlue
                Grade.Easy -> WaveTeal to Color.White
            }
            Button(
                onClick = { onGrade(grade) },
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(grade.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    intervals[grade]?.let { Text(intervalLabel(it), fontSize = 12.sp, maxLines = 1) }
                }
            }
        }
    }
}

/** A short label for an interval: "1d", "12d", "3mo", "1.5y". */
internal fun intervalLabel(days: Int): String = when {
    days < 30 -> "${days}d"
    days < 365 -> "${(days / 30.4).roundToInt()}mo"
    else -> "%.1fy".format(Locale.ROOT, days / 365.0).replace(".0y", "y")
}

/**
 * A study view that requires the user to type the translation of the native text, or with [isReversed]
 * the native text for the translation. A correct answer is graded with the grade buttons like in Flip mode;
 * a wrong one counts as "Again".
 *
 * @param currentCard The [Flashcard] data to test against.
 * @param nativeLang The user's native language, the one answers are typed in when [isReversed].
 * @param intervals Days until the card is due again after each grade, shown on the buttons.
 * @param onGrade Callback when the card is graded.
 * @param onSpeak Reads the translation aloud (once checked, or anytime when it is the question);
 *     null hides the button (no voice for the language).
 */
@Composable
fun TypeStudyView(
    currentCard: Flashcard,
    intervals: Map<Grade, Int>,
    onGrade: (Grade) -> Unit,
    nativeLang: String = "",
    isReversed: Boolean = false,
    onSpeak: (() -> Unit)? = null
) {
    var userInput by remember(currentCard) { mutableStateOf("") }
    var hasChecked by remember(currentCard) { mutableStateOf(false) }
    var isCorrect by remember(currentCard) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val question = if (isReversed) currentCard.targetText else currentCard.nativeText
    val answer = if (isReversed) currentCard.nativeText else currentCard.targetText

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Translate this:", color = SandMuted)

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)) {
            Text(
                text = question,
                style = MaterialTheme.typography.headlineSmall,
                color = SandBeige,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (isReversed && onSpeak != null) {
                IconButton(onClick = onSpeak) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Read aloud", tint = SandBeige)
                }
            }
        }

        OutlinedTextField(
            value = userInput,
            onValueChange = {
                userInput = it
                hasChecked = false // Hide feedback if they start typing again
            },
            label = { Text(if (isReversed && nativeLang.isNotEmpty()) "Type it in $nativeLang..." else "Type the translation...") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (!hasChecked) {
            Button(
                onClick = {
                    isCorrect = isCorrectAnswer(userInput, currentCard, isReversed)
                    hasChecked = true
                    haptics.performHapticFeedback(if (isCorrect) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
                },
                enabled = userInput.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WaveTeal, contentColor = Color.White)
            ) {
                Text("Check answer", fontSize = 18.sp)
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isCorrect) SandBeige else DeepOceanBlue,
                    contentColor = if (isCorrect) DeepOceanBlue else Color.White
                ),
                border = if (isCorrect) null else BorderStroke(1.dp, CoralText),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isCorrect) "✅ Perfect!" else "❌ Not quite",
                        style = MaterialTheme.typography.titleLarge
                    )
                    if (!isCorrect) {
                        Text("Correct answer:", modifier = Modifier.padding(top = 8.dp))
                        Text(
                            answer,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                    // The other correct translations: besides the one typed, or besides the answer shown above.
                    val others = if (isCorrect) {
                        (listOf(currentCard.targetText) + currentCard.alternatives).filterNot { answerMatches(userInput, it) }
                    } else {
                        currentCard.alternatives
                    }
                    if (!isReversed && currentCard.alternatives.isNotEmpty() && others.isNotEmpty()) {
                        Text(
                            text = "Also correct: ${others.joinToString(" · ")}",
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    // Reversed, the translation was the question and has its own speaker button.
                    if (!isReversed && onSpeak != null) {
                        TextButton(
                            onClick = onSpeak,
                            colors = ButtonDefaults.textButtonColors(contentColor = if (isCorrect) DeepOceanBlue else SandBeige)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                            Text("Listen")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (isCorrect) {
                GradeButtons(intervals = intervals, onGrade = onGrade)
            } else {
                // A wrong answer means the card wasn't remembered, so there is nothing to grade: it's "Again".
                Button(
                    onClick = { onGrade(Grade.Again) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("Continue", fontSize = 18.sp)
                }
                Text(
                    text = "This card comes back later in this session.",
                    fontSize = 13.sp,
                    color = SandMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

/**
 * A bottom sheet with a card's other correct translations: the note on how they differ, then each
 * alternative with a speaker button and "Make main", which swaps it with the card's translation.
 * It scrolls when the content is long.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlternativesSheet(
    card: Flashcard,
    onSpeak: ((String) -> Unit)?,
    onMakeMain: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DeepOceanBlue, contentColor = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            Text("Other ways to say it", style = MaterialTheme.typography.titleLarge, color = SandBeige)
            Spacer(modifier = Modifier.height(4.dp))
            Text(card.targetText, color = MistWhite, fontSize = 14.sp)
            card.note?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(it, fontStyle = FontStyle.Italic, color = MistWhite)
            }
            card.alternatives.forEach { alternative ->
                HorizontalDivider(color = SandBeige.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 12.dp))
                Text(alternative, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = SandBeige)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    if (onSpeak != null) {
                        IconButton(onClick = { onSpeak(alternative) }) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Read aloud", tint = SandBeige)
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { onMakeMain(alternative) }, colors = ButtonDefaults.textButtonColors(contentColor = SandBeige)) {
                        Text("Make main", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * Whether [typed] matches [card]'s translation or one of its alternatives (see [answerMatches]),
 * or its native text when studying [reversed].
 */
internal fun isCorrectAnswer(typed: String, card: Flashcard, reversed: Boolean = false): Boolean =
    if (reversed) answerMatches(typed, card.nativeText)
    else (listOf(card.targetText) + card.alternatives).any { answerMatches(typed, it) }

/**
 * Whether a typed answer matches the card's translation, ignoring case, punctuation and extra spaces
 * ("wo ist der Strand" matches "Wo ist der Strand?"). Accents and other letters must match exactly.
 */
internal fun answerMatches(typed: String, expected: String): Boolean = isSameSentence(typed, expected)

/**
 * Shown when the island has cards but none are due: when the next ones are, and a way to practice anyway.
 *
 * @param nextDueInDays Days until the next card is due, or null if unknown.
 * @param onPracticeAll Callback to study every card of the island anyway.
 * @param onBack Callback to navigate back to the previous screen.
 */
@Composable
fun CaughtUpView(nextDueInDays: Int?, onPracticeAll: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🌴", fontSize = 64.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "All caught up!",
            style = MaterialTheme.typography.headlineLarge,
            color = SandBeige,
            textAlign = TextAlign.Center
        )
        Text(
            text = when (nextDueInDays) {
                null -> "No cards are due on this island right now."
                1 -> "The next cards are due tomorrow."
                else -> "The next cards are due in $nextDueInDays days."
            },
            fontSize = 16.sp,
            color = SandMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )
        SessionEndButtons(onPracticeAll = onPracticeAll, onBack = onBack)
    }
}

/**
 * Shown when every card of the session was passed: a short celebration, the session's
 * stats and the streak.
 *
 * @param reviewedCount Cards in the session.
 * @param againCount How often "Again" was pressed.
 * @param onPracticeAll Callback to study every card of the island again.
 * @param onBack Callback to navigate back to the previous screen.
 */
@Composable
fun SessionCompleteView(
    reviewedCount: Int,
    againCount: Int,
    streakCount: Int,
    onPracticeAll: () -> Unit,
    onBack: () -> Unit
) {
    val emojiScale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        emojiScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Confetti(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🏝️", fontSize = 72.sp, modifier = Modifier.scale(emojiScale.value))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Island explored!",
                style = MaterialTheme.typography.headlineLarge,
                color = SandBeige,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (againCount == 0) "Every card on the first try. Brilliant!" else "You worked through every card.",
                fontSize = 16.sp,
                color = SandMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile(value = "$reviewedCount", label = if (reviewedCount == 1) "card" else "cards", modifier = Modifier.weight(1f))
                StatTile(value = "$againCount", label = "needed again", modifier = Modifier.weight(1f))
                StatTile(value = "🔥 $streakCount", label = if (streakCount == 1) "day streak" else "days streak", modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(32.dp))

            SessionEndButtons(onPracticeAll = onPracticeAll, onBack = onBack)
        }
    }
}

/** "Practice all cards" and "Back to islands", shared by the session end screens. */
@Composable
private fun SessionEndButtons(onPracticeAll: () -> Unit, onBack: () -> Unit) {
    Button(
        onClick = onPracticeAll,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SandBeige, contentColor = DeepOceanBlue)
    ) {
        Text("Practice all cards", fontSize = 18.sp)
    }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedButton(
        onClick = onBack,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        border = BorderStroke(1.dp, SandBeige),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = SandBeige)
    ) {
        Text("Back to islands", fontSize = 18.sp)
    }
}

/** A large [value] with a short [label] below it, for the session summary. */
@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardWash, contentColor = SandBeige)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp)
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, fontSize = 13.sp, color = MistWhite, textAlign = TextAlign.Center)
        }
    }
}

/** A one-off shower of confetti in the card tints, falling from the top of the screen. */
@Composable
private fun Confetti(modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(durationMillis = 2600, easing = LinearEasing)) }
    val pieces = remember {
        List(60) {
            ConfettiPiece(
                x = Random.nextFloat(),
                delay = Random.nextFloat() * 0.35f,
                speed = 0.8f + Random.nextFloat() * 0.6f,
                drift = (Random.nextFloat() - 0.5f) * 0.15f,
                color = (CardTints + SandBeige).random()
            )
        }
    }

    Canvas(modifier = modifier) {
        pieces.forEach { piece ->
            val t = ((progress.value - piece.delay) / (1f - piece.delay)).coerceIn(0f, 1f)
            if (t <= 0f || t >= 1f) return@forEach
            val x = (piece.x + piece.drift * t) * size.width
            val y = -20f + t * piece.speed * size.height
            drawCircle(color = piece.color.copy(alpha = 1f - t), radius = 5.dp.toPx(), center = Offset(x, y))
        }
    }
}

private data class ConfettiPiece(val x: Float, val delay: Float, val speed: Float, val drift: Float, val color: Color)

/**
 * Renders a sentence where each word can be tapped. Tappable words get a dotted underline;
 * punctuation stays attached to its word but isn't underlined or part of the lookup.
 *
 * @param sentence The full sentence to display.
 * @param onWordClick Callback triggered when a word is clicked, passing the sanitized word.
 *   If null, the words are shown as plain text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClickableWordSentence(sentence: String, onWordClick: ((String) -> Unit)?) {
    // FlowRow automatically wraps words to the next line
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        sentence.split(" ").filter { it.isNotEmpty() }.forEach { token ->
            val (leading, word, trailing) = splitPunctuation(token)
            Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                if (leading.isNotEmpty()) WordText(leading)
                if (word.isNotEmpty()) {
                    WordText(
                        text = word,
                        modifier = if (onWordClick == null) Modifier else Modifier
                            .dottedUnderline(DeepOceanBlue.copy(alpha = 0.6f))
                            .clickable { onWordClick(word) }
                    )
                }
                if (trailing.isNotEmpty()) WordText(trailing)
            }
        }
    }
}

/** A word or punctuation of a card's sentence, at the size cards are shown in. */
@Composable
private fun WordText(text: String, modifier: Modifier = Modifier) {
    Text(text = text, fontSize = 24.sp, fontWeight = FontWeight.Medium, modifier = modifier)
}

/** Splits "¿hola," into "¿", "hola" and ",": the word is everything from the first to the last letter or digit. */
internal fun splitPunctuation(token: String): Triple<String, String, String> {
    val first = token.indexOfFirst { it.isLetterOrDigit() }
    if (first == -1) return Triple(token, "", "")
    val last = token.indexOfLast { it.isLetterOrDigit() }
    return Triple(token.substring(0, first), token.substring(first, last + 1), token.substring(last + 1))
}

/** Draws a dotted line under the content, marking a word that can be looked up. */
private fun Modifier.dottedUnderline(color: Color) = drawBehind {
    val y = size.height - 2.dp.toPx()
    drawLine(
        color = color,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 5.dp.toPx()))
    )
}
