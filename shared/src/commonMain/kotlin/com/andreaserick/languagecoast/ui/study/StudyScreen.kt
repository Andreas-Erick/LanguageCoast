package com.andreaserick.languagecoast.ui.study

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andreaserick.languagecoast.data.AnswerMatch
import com.andreaserick.languagecoast.data.Flashcard
import com.andreaserick.languagecoast.data.Grade
import com.andreaserick.languagecoast.data.SuggestedSentence
import com.andreaserick.languagecoast.data.checkAnswer
import com.andreaserick.languagecoast.data.emojiForCategory
import com.andreaserick.languagecoast.data.isSameSentence
import com.andreaserick.languagecoast.ui.components.ButtonHeight
import com.andreaserick.languagecoast.ui.components.CardOptionsMenu
import com.andreaserick.languagecoast.ui.components.CoastButton
import com.andreaserick.languagecoast.ui.components.CoastSecondaryButton
import com.andreaserick.languagecoast.ui.components.CoastTextField
import com.andreaserick.languagecoast.ui.components.ContentCard
import com.andreaserick.languagecoast.ui.components.EditCardSheet
import com.andreaserick.languagecoast.ui.components.EmptyState
import com.andreaserick.languagecoast.ui.components.FeedbackKind
import com.andreaserick.languagecoast.ui.components.IconTile
import com.andreaserick.languagecoast.ui.components.LocalFeedback
import com.andreaserick.languagecoast.ui.components.LocalUndoMessenger
import com.andreaserick.languagecoast.ui.components.ScreenHeader
import com.andreaserick.languagecoast.ui.components.ScreenMargin
import com.andreaserick.languagecoast.ui.components.plural
import com.andreaserick.languagecoast.ui.theme.CoralText
import com.andreaserick.languagecoast.ui.theme.OnSunlit
import com.andreaserick.languagecoast.ui.theme.Seafoam
import com.andreaserick.languagecoast.ui.theme.SurfBlue
import com.andreaserick.languagecoast.ui.theme.SurfaceCard
import com.andreaserick.languagecoast.ui.theme.SurfaceRaised
import com.andreaserick.languagecoast.ui.theme.SurfaceSelected
import com.andreaserick.languagecoast.ui.theme.Sunlit
import com.andreaserick.languagecoast.ui.theme.TextPrimary
import com.andreaserick.languagecoast.ui.theme.TextSecondary
import com.andreaserick.languagecoast.util.dictCcSearchUrl
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The main screen for a study session: an island, a coast, today's cards across coasts or the leaky words
 * (see [StudyScope]). Cards can be flipped, typed in full or completed in a gap. A running session is a
 * focus mode: the app hides its bottom navigation, and everything that isn't answering the current card
 * is tucked into the ⋮ menu. The scope is taken from the navigation arguments by [StudyViewModel].
 *
 * @param onNavigateBack Callback to navigate back to the previous screen.
 * @param onShowCards Opens the list of the island's cards; null for sessions that span islands.
 */
@Composable
fun StudyScreen(onNavigateBack: () -> Unit, onShowCards: (() -> Unit)?, viewModel: StudyViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentCard = uiState.currentCard
    val currentPlaced = uiState.currentPlaced
    val undoMessenger = LocalUndoMessenger.current
    var editingCard by remember { mutableStateOf<Flashcard?>(null) }

    uiState.explanation?.let { explanation ->
        WordSheet(
            explanation = explanation,
            dictionaryUrl = dictCcSearchUrl(explanation.word, uiState.nativeLanguage, uiState.targetLanguage),
            onDismiss = viewModel::dismissExplanation
        )
    }

    uiState.cardCheck?.let { check ->
        CardCheckSheet(
            check = check,
            onApply = viewModel::applyCardCheck,
            onAskAlternative = viewModel::askAlternative,
            onSaveAlternative = viewModel::saveAlternative,
            onDismiss = viewModel::dismissCardCheck
        )
    }

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

    val learning = uiState.learning
    val inSession = uiState.hasCards && !uiState.isCaughtUp && !uiState.isSessionComplete && (currentCard != null || learning != null)

    Column(modifier = Modifier.fillMaxSize()) {
        if (inSession) {
            SessionTopBar(
                uiState = uiState,
                onClose = onNavigateBack,
                onShowCards = onShowCards,
                showCardOptions = currentCard != null,
                onPrevious = viewModel::previous,
                onSkip = viewModel::next,
                onCheck = viewModel::checkCard,
                onEdit = { editingCard = currentCard },
                onDelete = {
                    viewModel.deleteCurrentCard { deleted ->
                        undoMessenger.show("Card deleted") { viewModel.restore(deleted) }
                    }
                }
            )
        } else if (!uiState.isLoading) {
            ScreenHeader(
                title = uiState.islandName,
                onBack = onNavigateBack,
                actions = {
                    if (uiState.hasCards && onShowCards != null) {
                        IconButton(onClick = onShowCards) {
                            Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = "All cards", tint = TextPrimary)
                        }
                    }
                }
            )
        }

        when {
            uiState.isLoading -> Unit
            !uiState.hasCards -> EmptyState(icon = Icons.Default.Waves, title = "No flashcards here yet")
            uiState.isCaughtUp -> CaughtUpView(
                scope = uiState.scope,
                nextDueInDays = uiState.nextDueInDays,
                onPracticeAll = viewModel::practiceAll,
                onBack = onNavigateBack
            )
            uiState.isSessionComplete -> SessionCompleteView(
                scope = uiState.scope,
                reviewedCount = uiState.totalCards,
                againCount = uiState.againCount,
                streakCount = uiState.streakCount,
                keptCount = uiState.keptCount,
                onPracticeAll = viewModel::practiceAll,
                onBack = onNavigateBack
            )
            learning != null -> LearnView(
                sentence = learning,
                index = uiState.learnTotal - uiState.learnLeft + 1,
                total = uiState.learnTotal,
                onKeep = viewModel::keepSuggestion,
                onSkip = viewModel::skipSuggestion,
                onSpeak = { viewModel.speak(learning.targetText) }.takeIf { uiState.canSpeak }
            )
            currentCard != null && currentPlaced != null -> {
                StudyModeRow(
                    mode = uiState.mode,
                    onModeChange = viewModel::setMode,
                    nativeLanguage = uiState.nativeLanguage,
                    targetLanguage = uiState.targetLanguage,
                    isReversed = uiState.isReversed,
                    onReversedChange = viewModel::setReversed
                )
                val cloze = uiState.cloze
                val scrolls = uiState.mode != StudyMode.Flip

                // Typing mode scrolls when it doesn't fit (short screens, or with the keyboard open);
                // Flip mode sizes its card to the space instead.
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val scrollState = key(currentCard.cardId) { rememberScrollState() }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (scrolls) Modifier.verticalScroll(scrollState).heightIn(min = maxHeight)
                                else Modifier.fillMaxHeight()
                            )
                            .padding(start = ScreenMargin, end = ScreenMargin, top = 12.dp, bottom = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // key(cardId) resets per-card state (flip state, typed answer)
                        // when we move to a different card.
                        key(currentCard.cardId) {
                            when {
                                uiState.mode == StudyMode.Cloze && cloze != null -> ClozeStudyView(
                                    currentCard = currentCard,
                                    cloze = cloze,
                                    intervals = uiState.gradeIntervals,
                                    onGrade = viewModel::onGrade,
                                    onSpeak = viewModel::speakAnswer.takeIf { uiState.canSpeak }
                                )
                                // A one-word card has no gap to fill, so it is typed in full.
                                uiState.mode != StudyMode.Flip -> TypeStudyView(
                                    currentCard = currentCard,
                                    nativeLang = uiState.nativeLanguage,
                                    isReversed = uiState.isReversed && uiState.mode == StudyMode.Type,
                                    intervals = uiState.gradeIntervals,
                                    onGrade = viewModel::onGrade,
                                    onSpeak = viewModel::speakAnswer.takeIf { uiState.canSpeak }
                                )
                                else -> FlipStudyView(
                                    currentCard = currentCard,
                                    nativeLang = uiState.nativeLanguage,
                                    targetLang = uiState.targetLanguage,
                                    isReversed = uiState.isReversed,
                                    // Across islands the card says where it is from, as the title doesn't.
                                    islandName = if (uiState.scope == StudyScope.Island) uiState.islandName else "${currentPlaced.emoji} ${currentPlaced.islandName}",
                                    // Swiping is worth a reminder, but not on every card.
                                    showSwipeHint = uiState.sessionCards.size == uiState.totalCards,
                                    intervals = uiState.gradeIntervals,
                                    onGrade = viewModel::onGrade,
                                    onSpeak = viewModel::speakAnswer.takeIf { uiState.canSpeak },
                                    onSpeakText = viewModel::speak.takeIf { uiState.canSpeak },
                                    onMakeMain = viewModel::makeMainTranslation,
                                    onExplain = viewModel::explainWord
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The top of a running session: close, how far along it is, and a ⋮ menu with the rest of the actions.
 * Previous and Skip are only offered when there is more than one card to move between.
 */
@Composable
private fun SessionTopBar(
    uiState: StudyUiState,
    onClose: () -> Unit,
    onShowCards: (() -> Unit)?,
    showCardOptions: Boolean,
    onPrevious: () -> Unit,
    onSkip: () -> Unit,
    onCheck: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val progress by animateFloatAsState(uiState.progress, label = "progress")
    val canBrowse = uiState.sessionCards.size > 1
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 12.dp)
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Close session", tint = TextPrimary)
        }
        LinearProgressIndicator(
            progress = { progress },
            color = Sunlit,
            trackColor = SurfaceRaised,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
        )
        Text(
            text = if (uiState.learning != null) "${uiState.learnLeft} new" else "${uiState.sessionCards.size} left",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            maxLines = 1,
            modifier = Modifier.padding(start = 12.dp, end = if (showCardOptions) 4.dp else 16.dp)
        )
        if (showCardOptions) CardOptionsMenu(
            onEdit = onEdit,
            onDelete = onDelete,
            tint = TextPrimary,
            leadingItems = { dismiss ->
                if (canBrowse) {
                    DropdownMenuItem(
                        text = { Text("Previous card") },
                        enabled = uiState.canGoBack,
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
                        onClick = {
                            dismiss()
                            onPrevious()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Skip card") },
                        enabled = uiState.canGoForward,
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                        onClick = {
                            dismiss()
                            onSkip()
                        }
                    )
                }
                if (onShowCards != null) {
                    DropdownMenuItem(
                        text = { Text("All cards") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = null) },
                        onClick = {
                            dismiss()
                            onShowCards()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Check translation") },
                    leadingIcon = { Icon(Icons.Default.Spellcheck, contentDescription = null) },
                    onClick = {
                        dismiss()
                        onCheck()
                    }
                )
            }
        )
    }
}

/** How far (in dp) a card must be dragged to count as an Again/Good swipe. */
private const val SWIPE_THRESHOLD_DP = 110

/** How high the row of buttons under the flip card is, so that it doesn't resize when the card is flipped. */
private val GradeRowHeight = 68.dp

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
 * @param islandName Shown on the card, as the session's top bar leaves it out.
 * @param showSwipeHint Whether to explain swiping under the flipped card.
 * @param onSpeak Reads the translation aloud; null hides the speaker button (no voice for the language).
 * @param onSpeakText Reads an alternative aloud; null hides those speaker buttons.
 * @param onMakeMain Makes an alternative the card's translation.
 * @param onExplain Explains a word of the translation (see [WordSheet]); null leaves the words plain.
 */
@Composable
fun FlipStudyView(
    currentCard: Flashcard,
    nativeLang: String,
    targetLang: String,
    intervals: Map<Grade, Int>,
    onGrade: (Grade) -> Unit,
    isReversed: Boolean = false,
    islandName: String = "",
    showSwipeHint: Boolean = false,
    onSpeak: (() -> Unit)? = null,
    onSpeakText: ((String) -> Unit)? = null,
    onMakeMain: (String) -> Unit = {},
    onExplain: ((String) -> Unit)? = null
) {
    // Keyed on the card's ID, not the card: changing its translation must not flip it back.
    var isFlipped by remember(currentCard.cardId) { mutableStateOf(false) }
    var showAlternatives by remember(currentCard.cardId) { mutableStateOf(false) }
    val feedback = LocalFeedback.current
    val scope = rememberCoroutineScope()
    val offsetX = remember(currentCard.cardId) { Animatable(0f) }
    val thresholdPx = with(LocalDensity.current) { SWIPE_THRESHOLD_DP.dp.toPx() }
    // Reversed, the translation is the question, so explaining its words would give the answer away.
    val hasDictionary = !isReversed && onExplain != null

    // A new card settles into place rather than just appearing, which makes the change easy to notice.
    val arrival = remember(currentCard.cardId) { Animatable(0f) }
    LaunchedEffect(currentCard.cardId) { arrival.animateTo(1f, tween(durationMillis = 220)) }

    // Again re-queues the card, so with one card left the same card comes back: reset it explicitly.
    fun grade(grade: Grade) {
        feedback.play(if (grade == Grade.Again) FeedbackKind.Failure else FeedbackKind.Success)
        scope.launch {
            offsetX.snapTo(0f)
            isFlipped = false
            onGrade(grade)
        }
    }

    fun flip() {
        feedback.play(FeedbackKind.Tap)
        isFlipped = !isFlipped
    }

    // Animate the card rotation
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "flip"
    )

    val faceLanguage = when {
        isReversed && !isFlipped -> targetLang
        isReversed && isFlipped -> nativeLang
        !isReversed && !isFlipped -> nativeLang
        else -> targetLang
    }
    val question = if (isReversed) currentCard.targetText else currentCard.nativeText
    val answer = if (isReversed) currentCard.nativeText else currentCard.targetText

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Shown behind the card while it is dragged, to tell which way grades it how.
            if (isFlipped && offsetX.value != 0f) {
                SwipeLabel(offset = offsetX.value, thresholdPx = thresholdPx)
            }
            Card(
                onClick = ::flip,
                modifier = Modifier
                    .fillMaxSize()
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
                        alpha = arrival.value
                        scaleX = 0.97f + 0.03f * arrival.value
                        scaleY = scaleX
                    },
                shape = MaterialTheme.shapes.extraLarge,
                border = BorderStroke(1.dp, SurfaceSelected),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard, contentColor = TextPrimary)
            ) {
                // Keep content upright regardless of the card's rotation
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationY = if (isFlipped) 180f else 0f }
                ) {
                    Text(
                        text = listOf(islandName, faceLanguage).filter { it.isNotEmpty() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 24.dp, end = 56.dp, top = 20.dp)
                    )
                    // The translation can be heard once it is showing or, when it is the question, right away.
                    if (onSpeak != null && (isFlipped || isReversed)) {
                        IconButton(
                            onClick = onSpeak,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 6.dp, end = 6.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Read aloud", tint = Sunlit)
                        }
                    }

                    // Long sentences scroll inside the card instead of being cut off.
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 56.dp, bottom = 40.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp)
                        ) {
                            if (isFlipped) {
                                Text(question, style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                                HorizontalDivider(color = SurfaceSelected, modifier = Modifier.padding(vertical = 16.dp))
                                if (hasDictionary && onExplain != null) {
                                    ClickableWordSentence(sentence = answer, onWordClick = onExplain)
                                } else {
                                    Text(answer, style = MaterialTheme.typography.headlineLarge)
                                }
                            } else {
                                Text(question, style = MaterialTheme.typography.headlineLarge)
                            }
                        }
                    }

                    if (!isFlipped) {
                        Text(
                            text = "Tap to flip",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 14.dp)
                        )
                    }
                }
            }
        }

        // Always the same height, so the card doesn't jump when it is flipped.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
        ) {
            val hint = when {
                !isFlipped -> ""
                hasDictionary && showSwipeHint -> "Tap a word to explain it · Swipe ← Again, Good →"
                hasDictionary -> "Tap a word to explain it"
                showSwipeHint -> "Swipe ← Again, Good →"
                else -> ""
            }
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.weight(1f)
            )
            // Alternatives are other ways to say the translation, which only make sense when it is the answer.
            if (isFlipped && !isReversed && currentCard.alternatives.isNotEmpty()) {
                TextButton(
                    onClick = { showAlternatives = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary),
                    contentPadding = PaddingValues(start = 8.dp, end = 4.dp)
                ) {
                    Text(plural(currentCard.alternatives.size, "alternative"), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    Icon(Icons.Default.ExpandLess, contentDescription = null)
                }
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

        if (isFlipped) {
            GradeButtons(intervals = intervals, onGrade = ::grade)
        } else {
            CoastButton(
                onClick = ::flip,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(GradeRowHeight)
            ) {
                Text("Show answer")
            }
        }
    }
}

/** "Again" / "Good" while a card is dragged, fading in as the drag nears the threshold. */
@Composable
private fun SwipeLabel(offset: Float, thresholdPx: Float) {
    val good = offset > 0
    Text(
        text = if (good) "Good" else "Again",
        color = if (good) Sunlit else CoralText,
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .graphicsLayer { alpha = (abs(offset) / thresholdPx).coerceIn(0f, 1f) },
        textAlign = if (good) TextAlign.Start else TextAlign.End
    )
}

/**
 * The four grading buttons shared by both study modes, each showing when the card would come back
 * ("Soon" for Again, which repeats it later in the session).
 * "Good" is the usual choice, so it alone gets the accent; "Again" is told apart by its label's colour.
 */
@Composable
internal fun GradeButtons(intervals: Map<Grade, Int>, onGrade: (Grade) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        Grade.entries.forEach { grade ->
            val isGood = grade == Grade.Good
            CoastButton(
                onClick = { onGrade(grade) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = GradeRowHeight),
                contentPadding = PaddingValues(horizontal = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGood) Sunlit else SurfaceCard,
                    contentColor = if (isGood) OnSunlit else TextPrimary
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        grade.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (grade == Grade.Again) CoralText else Color.Unspecified,
                        maxLines = 1
                    )
                    // Again re-queues the card in this session; its saved interval only applies if the session is left.
                    val label = if (grade == Grade.Again) "Soon" else intervals[grade]?.let(::intervalLabel)
                    label?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isGood) OnSunlit else TextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/** A short label for an interval: "1d", "12d", "3mo", "1.5y". */
internal fun intervalLabel(days: Int): String = when {
    days < 30 -> "${days}d"
    days < 365 -> "${(days / 30.4).roundToInt()}mo"
    // One decimal, dropped when it's zero.
    else -> "${(days / 365.0 * 10).roundToInt() / 10.0}y".replace(".0y", "y")
}

/**
 * A study view that requires the user to type the translation of the native text, or with [isReversed]
 * the native text for the translation. A correct answer is graded with the grade buttons like in Flip mode;
 * one with a slip of a letter counts as "Hard", and a wrong one as "Again" (see [checkAnswer]).
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
    var match by remember(currentCard) { mutableStateOf<AnswerMatch?>(null) }
    val feedback = LocalFeedback.current
    val question = if (isReversed) currentCard.targetText else currentCard.nativeText
    val answer = if (isReversed) currentCard.nativeText else currentCard.targetText
    // On short screens the result doesn't fit under the question, so it scrolls into view once checked.
    val result = remember { BringIntoViewRequester() }
    LaunchedEffect(match) {
        if (match != null) result.bringIntoView()
    }

    fun check() {
        if (userInput.isBlank()) return
        val outcome = matchAnswer(userInput, currentCard, isReversed)
        match = outcome
        feedback.play(if (outcome == AnswerMatch.Wrong) FeedbackKind.Failure else FeedbackKind.Success)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        ContentCard(padding = PaddingValues(start = 20.dp, top = 16.dp, end = 8.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 40.dp)) {
                Text(
                    "Translate this",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                if (isReversed && onSpeak != null) {
                    IconButton(onClick = onSpeak) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Read aloud", tint = Sunlit)
                    }
                }
            }
            Text(
                text = question,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(end = 12.dp)
            )
        }

        CoastTextField(
            value = userInput,
            onValueChange = {
                userInput = it
                match = null // Hide feedback if they start typing again
            },
            label = { Text(if (isReversed && nativeLang.isNotEmpty()) "Type it in $nativeLang..." else "Type the translation...") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { check() }),
            modifier = Modifier.fillMaxWidth()
        )

        when (val outcome = match) {
            null -> CoastButton(
                onClick = ::check,
                enabled = userInput.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ButtonHeight)
            ) {
                Text("Check answer")
            }
            else -> Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(result)
            ) {
                // The other correct translations: besides the one typed, or besides the answer shown.
                val others = when {
                    isReversed -> emptyList()
                    outcome == AnswerMatch.Exact -> (listOf(currentCard.targetText) + currentCard.alternatives).filterNot { answerMatches(userInput, it) }
                    else -> currentCard.alternatives
                }
                AnswerVerdict(
                    outcome = outcome,
                    expected = (outcome as? AnswerMatch.Almost)?.expected ?: answer,
                    alsoCorrect = if (currentCard.alternatives.isNotEmpty()) others else emptyList(),
                    // Reversed, the translation was the question and has its own speaker button.
                    onSpeak = onSpeak.takeIf { !isReversed }
                )
                when (outcome) {
                    AnswerMatch.Exact -> GradeButtons(
                        intervals = intervals,
                        onGrade = { grade ->
                            feedback.play(if (grade == Grade.Again) FeedbackKind.Failure else FeedbackKind.Success)
                            onGrade(grade)
                        }
                    )
                    is AnswerMatch.Almost -> HardContinue(onGrade = onGrade)
                    AnswerMatch.Wrong -> AgainContinue(onGrade = onGrade)
                }
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            Text("Other ways to say it", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(card.targetText, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            card.note?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(it, fontStyle = FontStyle.Italic, color = TextSecondary)
            }
            card.alternatives.forEach { alternative ->
                HorizontalDivider(color = SurfaceRaised, modifier = Modifier.padding(vertical = 12.dp))
                Text(alternative, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    if (onSpeak != null) {
                        IconButton(onClick = { onSpeak(alternative) }) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Read aloud", tint = Sunlit)
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { onMakeMain(alternative) }) {
                        Text("Make main", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/**
 * How [typed] compares with [card]'s translation or one of its alternatives (see [checkAnswer]),
 * or with its native text when studying [reversed].
 */
internal fun matchAnswer(typed: String, card: Flashcard, reversed: Boolean = false): AnswerMatch =
    checkAnswer(typed, if (reversed) listOf(card.nativeText) else listOf(card.targetText) + card.alternatives)

/**
 * Whether [typed] matches [card]'s translation or one of its alternatives (see [answerMatches]),
 * or its native text when studying [reversed].
 */
internal fun isCorrectAnswer(typed: String, card: Flashcard, reversed: Boolean = false): Boolean =
    matchAnswer(typed, card, reversed) == AnswerMatch.Exact

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
fun CaughtUpView(nextDueInDays: Int?, onPracticeAll: () -> Unit, onBack: () -> Unit, scope: StudyScope = StudyScope.Island) {
    EmptyState(
        icon = Icons.Default.BeachAccess,
        title = if (scope == StudyScope.Leaky) "Nothing leaking" else "All caught up",
        body = caughtUpBody(scope, nextDueInDays),
        action = { SessionEndButtons(scope = scope, onPracticeAll = onPracticeAll, onBack = onBack) }
    )
}

/**
 * Shown when every card of the session was passed: a short celebration, the session's
 * stats and the streak.
 *
 * @param reviewedCount Cards in the session.
 * @param againCount How often "Again" was pressed.
 * @param keptCount New sentences kept in a daily session.
 * @param onPracticeAll Callback to study every card of the island again.
 * @param onBack Callback to navigate back to the previous screen.
 */
@Composable
fun SessionCompleteView(
    reviewedCount: Int,
    againCount: Int,
    streakCount: Int,
    onPracticeAll: () -> Unit,
    onBack: () -> Unit,
    scope: StudyScope = StudyScope.Island,
    keptCount: Int = 0
) {
    val markScale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        markScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Confetti(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ScreenMargin),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .scale(markScale.value)
                    .background(Sunlit, CircleShape)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = OnSunlit, modifier = Modifier.size(40.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = completionTitle(scope),
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = when {
                    scope == StudyScope.Daily && keptCount > 0 -> "${plural(keptCount, "new sentence")} kept. That's today done; see you tomorrow."
                    scope == StudyScope.Daily -> "That's today done; see you tomorrow."
                    againCount == 0 -> "Every card on the first try. Brilliant!"
                    else -> "You worked through every card."
                },
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 24.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                StatTile(value = "$reviewedCount", label = if (reviewedCount == 1) "card" else "cards", modifier = Modifier.weight(1f).fillMaxHeight())
                if (scope == StudyScope.Daily) StatTile(value = "$keptCount", label = "new", modifier = Modifier.weight(1f).fillMaxHeight())
                else StatTile(value = "$againCount", label = "needed again", modifier = Modifier.weight(1f).fillMaxHeight())
                StatTile(value = "$streakCount", label = "day streak", modifier = Modifier.weight(1f).fillMaxHeight())
            }

            Spacer(modifier = Modifier.height(24.dp))

            SessionEndButtons(scope = scope, onPracticeAll = onPracticeAll, onBack = onBack)
        }
    }
}

/** "Back to islands" and "Practice all cards", shared by the session end screens. Going back is the usual next step. */
@Composable
private fun SessionEndButtons(scope: StudyScope, onPracticeAll: () -> Unit, onBack: () -> Unit) {
    CoastButton(
        onClick = onBack,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ButtonHeight)
    ) {
        Text(backLabel(scope))
    }

    Spacer(modifier = Modifier.height(10.dp))

    CoastSecondaryButton(
        onClick = onPracticeAll,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ButtonHeight)
    ) {
        Text("Practice all cards")
    }
}

/** A large [value] with a short [label] below it, for the session summary. */
@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    ContentCard(modifier = modifier, padding = PaddingValues(vertical = 14.dp, horizontal = 8.dp)) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** A one-off shower of confetti, falling from the top of the screen. */
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
                color = listOf(Sunlit, SurfBlue, Seafoam, CoralText).random()
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
    val style = MaterialTheme.typography.headlineLarge
    // FlowRow automatically wraps words to the next line
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        sentence.split(" ").filter { it.isNotEmpty() }.forEach { token ->
            val (leading, word, trailing) = splitPunctuation(token)
            Row {
                if (leading.isNotEmpty()) WordText(leading, style)
                if (word.isNotEmpty()) {
                    WordText(
                        text = word,
                        style = style,
                        modifier = if (onWordClick == null) Modifier else Modifier
                            .dottedUnderline(TextSecondary)
                            .clickable { onWordClick(word) }
                    )
                }
                if (trailing.isNotEmpty()) WordText(trailing, style)
            }
        }
    }
}

/** A word or punctuation of a card's sentence, at the size cards are shown in. */
@Composable
private fun WordText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    Text(text = text, style = style, modifier = modifier)
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
    val y = size.height - 1.dp.toPx()
    drawLine(
        color = color,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 5.dp.toPx()))
    )
}
