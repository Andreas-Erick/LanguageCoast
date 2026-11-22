package com.andreaserick.languagecoast.ui.study

import com.andreaserick.languagecoast.data.answerMatches
import com.andreaserick.languagecoast.data.isLookupWord
import com.andreaserick.languagecoast.data.matchAnswer
import com.andreaserick.languagecoast.data.lookupWords
import com.andreaserick.languagecoast.data.splitPunctuation
import com.andreaserick.languagecoast.data.StudyScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.material.icons.filled.QuestionAnswer
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andreaserick.languagecoast.data.AnswerMatch
import com.andreaserick.languagecoast.data.Flashcard
import com.andreaserick.languagecoast.data.Grade
import com.andreaserick.languagecoast.data.SuggestedSentence
import com.andreaserick.languagecoast.data.checkAnswer
import com.andreaserick.languagecoast.data.emojiForCategory
import com.andreaserick.languagecoast.data.isListItem
import com.andreaserick.languagecoast.ui.components.ButtonHeight
import com.andreaserick.languagecoast.ui.components.ButtonLabel
import com.andreaserick.languagecoast.ui.components.PaceSheet
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
import com.andreaserick.languagecoast.ui.components.LocalReducedMotion
import com.andreaserick.languagecoast.ui.components.LocalUndoMessenger
import com.andreaserick.languagecoast.ui.components.ScreenHeader
import com.andreaserick.languagecoast.ui.components.ScreenMargin
import com.andreaserick.languagecoast.ui.components.inLanguage
import com.andreaserick.languagecoast.ui.components.plural
import com.andreaserick.languagecoast.ui.theme.CoralText
import com.andreaserick.languagecoast.ui.theme.OnSunlit
import com.andreaserick.languagecoast.ui.theme.Seafoam
import com.andreaserick.languagecoast.ui.theme.SurfBlue
import com.andreaserick.languagecoast.ui.theme.SurfaceCard
import com.andreaserick.languagecoast.ui.theme.SurfaceRaised
import com.andreaserick.languagecoast.ui.theme.SurfaceSelected
import com.andreaserick.languagecoast.ui.theme.Sunlit
import com.andreaserick.languagecoast.ui.theme.SunlitText
import com.andreaserick.languagecoast.ui.theme.TextPrimary
import com.andreaserick.languagecoast.ui.theme.TextSecondary
import com.andreaserick.languagecoast.ui.theme.tabular
import com.andreaserick.languagecoast.util.dictCcSearchUrl
import kotlinx.coroutines.delay
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
 * @param onKeepGoing Opens a bonus round on coast `coastId` in `language` ([com.andreaserick.languagecoast.navigation.StudyScreenRoute.bonus]),
 *     offered at the end of the user's first daily session; null leaves "Keep going" out.
 */
@Composable
fun StudyScreen(
    onNavigateBack: () -> Unit,
    onShowCards: (() -> Unit)?,
    onKeepGoing: ((coastId: Int, language: String) -> Unit)? = null,
    viewModel: StudyViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showPaceSheet by rememberSaveable { mutableStateOf(false) }
    if (showPaceSheet) {
        PaceSheet(pace = uiState.pace, waiting = uiState.newWaiting, onPick = viewModel::setPace, onDismiss = { showPaceSheet = false })
    }
    val currentCard = uiState.currentCard
    val currentPlaced = uiState.currentPlaced
    val undoMessenger = LocalUndoMessenger.current
    var editingCardId by rememberSaveable { mutableStateOf<Int?>(null) }
    val editingCard = editingCardId?.let { id ->
        if (currentCard?.cardId == id) currentCard else null
    }

    uiState.explanation?.let { explanation ->
        WordSheet(
            explanation = explanation,
            dictionaryUrl = dictCcSearchUrl(explanation.word, uiState.nativeLanguage, uiState.targetLanguage),
            onRetry = { viewModel.explainWord(explanation.word) },
            onDismiss = viewModel::dismissExplanation
        )
    }

    uiState.cardCheck?.let { check ->
        CardCheckSheet(
            check = check,
            onApply = {
                viewModel.applyCardCheck { cardId, previous ->
                    undoMessenger.show("Translation replaced") { viewModel.restoreCard(cardId, previous) }
                }
            },
            onAskAlternative = viewModel::askAlternative,
            onSaveAlternative = viewModel::saveAlternative,
            onRetry = viewModel::checkCard,
            onDismiss = viewModel::dismissCardCheck
        )
    }

    uiState.cardQuestions?.let { questions ->
        CardQuestionSheet(questions = questions, onAsk = viewModel::askAboutCard, onDismiss = viewModel::dismissCardQuestions)
    }

    editingCard?.let { card ->
        EditCardSheet(
            card = card,
            islands = uiState.coastIslands,
            nativeLanguage = uiState.nativeLanguage,
            targetLanguage = uiState.targetLanguage,
            onSave = { edit ->
                viewModel.editCard(card.cardId, edit)
                editingCardId = null
            },
            onDismiss = { editingCardId = null }
        )
    }

    val learning = uiState.learning
    val introducing = uiState.introducing
    val inSession = uiState.hasCards && !uiState.isCaughtUp && !uiState.isSessionComplete && (currentCard != null || learning != null || introducing != null)

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
                onAsk = viewModel::openCardQuestions,
                onEdit = { editingCardId = currentCard?.cardId },
                onDelete = {
                    viewModel.deleteCurrentCard { deleted ->
                        undoMessenger.show("Card deleted") { viewModel.restore(deleted) }
                    }
                }
            )
        } else {
            // Also while the cards load, so there is always a way back.
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
            !uiState.hasCards -> EmptyState(icon = Icons.Default.Waves, title = "No cards here yet")
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
                keptCount = uiState.keptCount + uiState.learnedCount,
                isCounted = uiState.countsAsStudied,
                canPracticeAll = uiState.canPracticeAll,
                onPracticeAll = viewModel::practiceAll,
                onBack = onNavigateBack,
                firstSession = if (uiState.scope == StudyScope.Daily && uiState.isFirstSession && uiState.countsAsStudied) {
                    FirstSessionEnd(
                        bonusCount = uiState.bonusAvailable,
                        onKeepGoing = onKeepGoing?.takeIf { uiState.bonusAvailable > 0 && uiState.scopeLanguage.isNotEmpty() }
                            ?.let { keepGoing -> { keepGoing(viewModel.coastId, uiState.scopeLanguage) } },
                        onPickPace = { showPaceSheet = true }
                    )
                } else null
            )
            introducing != null -> IntroduceView(
                card = introducing,
                index = uiState.introTotal - uiState.introLeft + 1,
                total = uiState.introTotal,
                // Each tap names the card it was for, so a quick second tap can't decide the next one too.
                onLearn = { viewModel.learnIntroduced(introducing.card.cardId) },
                onKnow = {
                    viewModel.knowIntroduced(introducing.card.cardId) { known ->
                        undoMessenger.show("Marked as known") { viewModel.undoKnowIntroduced(known) }
                    }
                },
                onSpeak = { viewModel.speak(introducing.card.targetText) }.takeIf { uiState.canSpeak },
                targetLanguage = introducing.language,
                nativeLanguage = uiState.nativeLanguage
            )
            learning != null -> LearnView(
                sentence = learning,
                index = uiState.learnTotal - uiState.learnLeft + 1,
                total = uiState.learnTotal,
                onKeep = { viewModel.keepSuggestion(learning.suggestionId) },
                onSkip = {
                    viewModel.skipSuggestion(learning.suggestionId) { skipped ->
                        undoMessenger.show("Sentence skipped") { viewModel.undoSkipSuggestion(skipped) }
                    }
                },
                onSpeak = { viewModel.speak(learning.targetText) }.takeIf { uiState.canSpeak },
                targetLanguage = uiState.targetLanguage,
                nativeLanguage = uiState.nativeLanguage
            )
            currentCard != null && currentPlaced != null -> {
                // Fill gap is left out for a language that doesn't separate its words; such a card is typed in full.
                val mode = uiState.cardMode
                StudyModeRow(
                    mode = mode,
                    onModeChange = viewModel::setMode,
                    nativeLanguage = uiState.nativeLanguage,
                    targetLanguage = uiState.targetLanguage,
                    isReversed = uiState.isReversed,
                    onReversedChange = viewModel::setReversed,
                    showFillGap = uiState.hasSpacedWords
                )
                val cloze = uiState.cloze
                val scrolls = mode != StudyMode.Flip

                // Typing mode scrolls when it doesn't fit (short screens, or with the keyboard open). Flip mode
                // sizes its card to the space, but not below a height the card can be read at: in a short window
                // (a phone on its side, split screen) it scrolls too.
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val scrollState = key(currentCard.cardId) { rememberScrollState() }
                    // Graded Again with one card left, the same card comes back: it starts at the top like any other card.
                    LaunchedEffect(uiState.againCount) { scrollState.scrollTo(0) }
                    // Too short for the flip card at a height it can be read at.
                    val isShort = maxHeight < FlipMinHeight
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            // In a tall window Flip mode has nothing to scroll, so it gets no scroll at all: even an
                            // idle one would take steep swipes from the card and clip its corners while it is swiped.
                            .then(if (scrolls || isShort) Modifier.verticalScroll(scrollState) else Modifier)
                            .then(
                                if (scrolls) Modifier.heightIn(min = maxHeight)
                                else Modifier.height(maxOf(maxHeight, FlipMinHeight))
                            )
                            .padding(start = ScreenMargin, end = ScreenMargin, top = 12.dp, bottom = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // key(cardId) resets per-card state (flip state, typed answer)
                        // when we move to a different card.
                        key(currentCard.cardId) {
                            when {
                                mode == StudyMode.Cloze && cloze != null -> ClozeStudyView(
                                    currentCard = currentCard,
                                    cloze = cloze,
                                    intervals = uiState.gradeIntervals,
                                    onGrade = { grade -> viewModel.onGrade(grade, currentCard.cardId) },
                                    onSpeak = viewModel::speakAnswer.takeIf { uiState.canSpeak },
                                    nativeLang = uiState.nativeLanguage,
                                    targetLang = uiState.targetLanguage
                                )
                                // A one-word card has no gap to fill, so it is typed in full.
                                mode != StudyMode.Flip -> TypeStudyView(
                                    currentCard = currentCard,
                                    nativeLang = uiState.nativeLanguage,
                                    targetLang = uiState.targetLanguage,
                                    isReversed = uiState.isReversed && mode == StudyMode.Type,
                                    intervals = uiState.gradeIntervals,
                                    onGrade = { grade -> viewModel.onGrade(grade, currentCard.cardId) },
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
                                    onGrade = { grade -> viewModel.onGrade(grade, currentCard.cardId) },
                                    onSpeak = viewModel::speakAnswer.takeIf { uiState.canSpeak },
                                    onSpeakText = viewModel::speak.takeIf { uiState.canSpeak },
                                    onMakeMain = viewModel::makeMainTranslation,
                                    // A sentence can only be tapped word by word where spaces show the words.
                                    onExplain = viewModel::explainWord.takeIf { uiState.hasSpacedWords },
                                    compact = isShort
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
    onAsk: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val reducedMotion = LocalReducedMotion.current
    val progress by animateFloatAsState(
        targetValue = uiState.progress,
        animationSpec = if (reducedMotion) snap() else spring(),
        label = "progress"
    )
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
            text = when {
                uiState.introducing != null -> "${uiState.introLeft + uiState.learnLeft} new"
                uiState.learning != null -> "${uiState.learnLeft} new"
                else -> "${uiState.sessionCards.size} left"
            },
            // Even-width digits and a fixed least width, so the bar beside it doesn't change length as the count drops.
            style = MaterialTheme.typography.labelMedium.tabular,
            color = TextSecondary,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier
                .padding(start = 12.dp, end = if (showCardOptions) 4.dp else 16.dp)
                .widthIn(min = 52.dp)
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
                DropdownMenuItem(
                    text = { Text("Ask about this card") },
                    leadingIcon = { Icon(Icons.Default.QuestionAnswer, contentDescription = null) },
                    onClick = {
                        dismiss()
                        onAsk()
                    }
                )
            }
        )
    }
}

/** How far (in dp) a card must be dragged to count as an Again/Good swipe. */
private const val SWIPE_THRESHOLD_DP = 110

/** How long the flip card takes to turn over, in milliseconds. */
private const val FLIP_MILLIS = 400

/** How high the row of buttons under the flip card is, so that it doesn't resize when the card is flipped. */
private val GradeRowHeight = 68.dp

/**
 * The least height Flip mode is laid out at, which leaves the card about 216 dp; a shorter window (a phone on
 * its side, split screen) scrolls. Small phones held upright leave about 380 to 400 dp and must not count.
 */
private val FlipMinHeight = 360.dp

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
 * @param compact The window is too short for the whole card, so the page scrolls: the text then starts at the
 *     top of the card instead of its middle, and the answer is scrolled to when the card is flipped.
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
    onExplain: ((String) -> Unit)? = null,
    compact: Boolean = false
) {
    // Keyed on the card's ID, not the card: changing its translation must not flip it back.
    var isFlipped by rememberSaveable(currentCard.cardId) { mutableStateOf(false) }
    var showAlternatives by remember(currentCard.cardId) { mutableStateOf(false) }
    // In a short window the answer can sit below the fold, so it is scrolled to once the card has turned
    // (while it turns, its tilt shifts where the answer seems to be).
    val answerInView = remember { BringIntoViewRequester() }
    LaunchedEffect(isFlipped, compact) {
        if (compact && isFlipped) {
            delay(FLIP_MILLIS.toLong())
            answerInView.bringIntoView()
        }
    }
    val feedback = LocalFeedback.current
    val scope = rememberCoroutineScope()
    val offsetX = remember(currentCard.cardId) { Animatable(0f) }
    val thresholdPx = with(LocalDensity.current) { SWIPE_THRESHOLD_DP.dp.toPx() }
    // Reversed, the translation is the question, so explaining its words would give the answer away.
    val hasDictionary = !isReversed && onExplain != null

    val reducedMotion = com.andreaserick.languagecoast.ui.components.LocalReducedMotion.current
    // A new card settles into place rather than just appearing, which makes the change easy to notice.
    val arrival = remember(currentCard.cardId) { Animatable(if (reducedMotion) 1f else 0f) }
    // Keyed on the card, not its ID: graded Again with one card left, the same card comes back and must be gradable again.
    var gradedCardId by remember(currentCard) { mutableStateOf<Int?>(null) }
    LaunchedEffect(currentCard.cardId, reducedMotion) {
        if (reducedMotion) arrival.snapTo(1f) else arrival.animateTo(1f, tween(durationMillis = 220))
    }

    // Again re-queues the card, so with one card left the same card comes back: reset it explicitly.
    // Guard against rapid double-taps grading the next card before recomposition settles.
    fun grade(grade: Grade) {
        if (gradedCardId == currentCard.cardId) return
        gradedCardId = currentCard.cardId
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

    // Animate the card rotation (snap immediately when OS reduced-motion is enabled).
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else FLIP_MILLIS),
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
    val questionLang = if (isReversed) targetLang else nativeLang
    val answerLang = if (isReversed) nativeLang else targetLang

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
                    // For screen readers the card is one item: it says which side is up and what a tap does,
                    // and is read out again when it is turned or a new card arrives.
                    .semantics {
                        stateDescription = if (isFlipped) "Answer" else "Question"
                        onClick(label = if (isFlipped) "flip back" else "show the answer", action = null)
                        liveRegion = LiveRegionMode.Polite
                        // The answer's words as actions, in place of the taps: a screen reader stops at the card,
                        // never at a word inside it, and offers only the actions of the item it stops at.
                        // Android allows at most 31 custom actions on one item and throws beyond that.
                        if (isFlipped && hasDictionary) {
                            customActions = lookupWords(answer).take(31).map { word ->
                                CustomAccessibilityAction("Explain $word") {
                                    onExplain(word)
                                    true
                                }
                            }
                        }
                    }
                    // Must come before the rotated graphicsLayer: inside it, the flipped (180°) card
                    // would mirror drag directions and a swipe right would count as "Again".
                    .pointerInput(isFlipped, reducedMotion) {
                        if (!isFlipped) return@pointerInput
                        // Whether the drag is far enough to grade, so the tick plays once on the way in, not on every move.
                        var pastThreshold = false
                        // A card let go short of the threshold springs back, or just goes back with reduced motion.
                        fun settle() {
                            scope.launch {
                                if (reducedMotion) offsetX.snapTo(0f)
                                else offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            }
                        }
                        detectHorizontalDragGestures(
                            onDragStart = { pastThreshold = abs(offsetX.value) > thresholdPx },
                            onDragEnd = {
                                when {
                                    offsetX.value > thresholdPx -> grade(Grade.Good)
                                    offsetX.value < -thresholdPx -> grade(Grade.Again)
                                    else -> settle()
                                }
                            },
                            onDragCancel = { settle() },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    offsetX.snapTo(offsetX.value + dragAmount)
                                    val past = abs(offsetX.value) > thresholdPx
                                    // A light tick as the card crosses into grading, so a swipe can be felt to count.
                                    if (past && !pastThreshold) feedback.play(FeedbackKind.Tap)
                                    pastThreshold = past
                                }
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
                        overflow = TextOverflow.Ellipsis,
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
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Read aloud", tint = SunlitText)
                        }
                    }

                    // Long sentences scroll inside the card instead of being cut off.
                    Box(
                        contentAlignment = if (compact) Alignment.TopStart else Alignment.CenterStart,
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
                                Text(inLanguage(question, questionLang), style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                                HorizontalDivider(color = SurfaceSelected, modifier = Modifier.padding(vertical = 16.dp))
                                Box(modifier = Modifier.bringIntoViewRequester(answerInView)) {
                                    CardSideText(text = answer, language = answerLang, onWordClick = onExplain.takeIf { hasDictionary })
                                }
                            } else {
                                CardSideText(text = question, language = questionLang)
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
                                .padding(bottom = 16.dp)
                                // The card already says what a tap does.
                                .clearAndSetSemantics {}
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
                hasDictionary && showSwipeHint -> "Tap a word to explain it. Swipe left for Again, right for Good."
                hasDictionary -> "Tap a word to explain it"
                showSwipeHint -> "Swipe left for Again, right for Good."
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
                language = targetLang,
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
                    .heightIn(min = GradeRowHeight)
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
        color = if (good) SunlitText else CoralText,
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
            // Again re-queues the card in this session; its saved interval only applies if the session is left.
            val days = if (grade == Grade.Again) null else intervals[grade]
            val nameStyle = MaterialTheme.typography.titleSmall
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    // Read as one phrase: a screen reader would spell out "3mo" letter by letter.
                    modifier = Modifier.clearAndSetSemantics { contentDescription = gradeDescription(grade, days) }
                ) {
                    Text(
                        grade.name,
                        style = nameStyle,
                        color = if (grade == Grade.Again) CoralText else Color.Unspecified,
                        maxLines = 1,
                        // With a large font size the name shrinks to its quarter of the row instead of losing letters.
                        autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = nameStyle.fontSize)
                    )
                    val label = if (grade == Grade.Again) "Soon" else days?.let(::intervalLabel)
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

/** [intervalLabel] in full words, for screen readers: "1 day", "12 days", "3 months", "1.5 years". */
internal fun spokenInterval(days: Int): String = when {
    days < 30 -> plural(days, "day")
    days < 365 -> plural((days / 30.4).roundToInt(), "month")
    else -> {
        val years = (days / 365.0 * 10).roundToInt() / 10.0
        if (years == 1.0) "1 year" else "${years.toString().removeSuffix(".0")} years"
    }
}

/** What a grade button says to a screen reader: "Good, back in 3 days"; [days] is null when no interval is shown. */
internal fun gradeDescription(grade: Grade, days: Int?): String = when {
    grade == Grade.Again -> "Again, back later in this session"
    days == null -> grade.name
    days < 1 -> "${grade.name}, back today"
    else -> "${grade.name}, back in ${spokenInterval(days)}"
}

/**
 * A study view that requires the user to type the translation of the native text, or with [isReversed]
 * the native text for the translation. A correct answer is graded with the grade buttons like in Flip mode;
 * one with a slip of a letter counts as "Hard", and a wrong one as "Again" (see [checkAnswer]).
 *
 * @param currentCard The [Flashcard] data to test against.
 * @param nativeLang The user's native language, the one answers are typed in when [isReversed].
 * @param targetLang The language of the card's translation.
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
    onSpeak: (() -> Unit)? = null,
    targetLang: String = ""
) {
    // Keyed on the card, not its ID: graded Again with one card left, the same card comes back and starts empty.
    // The typed answer is kept when the screen is rotated or resized; the verdict is one tap on Check away.
    var userInput by rememberSaveable(currentCard) { mutableStateOf("") }
    var match by remember(currentCard) { mutableStateOf<AnswerMatch?>(null) }
    val feedback = LocalFeedback.current
    val focusManager = LocalFocusManager.current
    val question = if (isReversed) currentCard.targetText else currentCard.nativeText
    val answer = if (isReversed) currentCard.nativeText else currentCard.targetText
    val questionLang = if (isReversed) targetLang else nativeLang
    val answerLang = if (isReversed) nativeLang else targetLang
    // On short screens the result doesn't fit under the question, so it scrolls into view once checked.
    val result = remember { BringIntoViewRequester() }
    LaunchedEffect(match) {
        if (match != null) result.bringIntoView()
    }

    fun check() {
        if (userInput.isBlank()) return
        val outcome = matchAnswer(userInput, currentCard, isReversed)
        match = outcome
        // The keyboard has done its part; the verdict and the grade buttons need the room.
        focusManager.clearFocus()
        feedback.play(if (outcome == AnswerMatch.Wrong) FeedbackKind.Failure else FeedbackKind.Success)
    }

    // One grade per card: a quick second tap would otherwise grade the same card again when Again brings it
    // straight back. (A tap that lands on the next card is turned away by the ViewModel.)
    var isGraded by remember(currentCard) { mutableStateOf(false) }
    fun grade(grade: Grade, playFeedback: Boolean) {
        if (isGraded) return
        isGraded = true
        if (playFeedback) feedback.play(if (grade == Grade.Again) FeedbackKind.Failure else FeedbackKind.Success)
        onGrade(grade)
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
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Read aloud", tint = SunlitText)
                    }
                }
            }
            Text(
                text = inLanguage(question, questionLang),
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
            label = { Text(if (isReversed && nativeLang.isNotEmpty()) "Type it in $nativeLang…" else "Type the translation…") },
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
                    onSpeak = onSpeak.takeIf { !isReversed },
                    language = answerLang
                )
                when (outcome) {
                    AnswerMatch.Exact -> GradeButtons(intervals = intervals, onGrade = { grade(it, playFeedback = true) })
                    is AnswerMatch.Almost -> HardContinue(onGrade = { grade(it, playFeedback = false) })
                    AnswerMatch.Wrong -> AgainContinue(onGrade = { grade(it, playFeedback = false) })
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
    language: String,
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
            Text("Other ways to say it", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Spacer(modifier = Modifier.height(4.dp))
            Text(inLanguage(card.targetText, language), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            card.note?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(it, fontStyle = FontStyle.Italic, color = TextSecondary)
            }
            card.alternatives.forEach { alternative ->
                HorizontalDivider(color = SurfaceRaised, modifier = Modifier.padding(vertical = 12.dp))
                Text(inLanguage(alternative, language), style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    if (onSpeak != null) {
                        IconButton(onClick = { onSpeak(alternative) }) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Read aloud", tint = SunlitText)
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = { onMakeMain(alternative) },
                        colors = ButtonDefaults.textButtonColors(contentColor = SunlitText)
                    ) {
                        Text("Make main", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

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
 * @param isCounted Whether the session counts as a day studied. One in which every offered sentence was
 *     skipped doesn't, and is told plainly instead of celebrated.
 * @param onPracticeAll Callback to study every card of the island again.
 * @param onBack Callback to navigate back to the previous screen.
 * @param canPracticeAll Whether there is any card to practice; without one "Practice all cards" is left out.
 * @param firstSession What the end of the user's first daily session adds; null for every other session.
 */
@Composable
fun SessionCompleteView(
    reviewedCount: Int,
    againCount: Int,
    streakCount: Int,
    onPracticeAll: () -> Unit,
    onBack: () -> Unit,
    scope: StudyScope = StudyScope.Island,
    keptCount: Int = 0,
    isCounted: Boolean = true,
    canPracticeAll: Boolean = true,
    firstSession: FirstSessionEnd? = null
) {
    val reducedMotion = com.andreaserick.languagecoast.ui.components.LocalReducedMotion.current
    val markScale = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) markScale.snapTo(1f)
        else markScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isCounted && !reducedMotion) Confetti(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                // Centred while it fits; in a short window (a phone on its side, large text) the buttons are scrolled to.
                .verticalScroll(rememberScrollState())
                .padding(ScreenMargin),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isCounted) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .graphicsLayer {
                            scaleX = markScale.value
                            scaleY = markScale.value
                        }
                        .background(Sunlit, CircleShape)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = OnSunlit, modifier = Modifier.size(40.dp))
                }
            } else {
                IconTile(size = 64.dp) {
                    Icon(Icons.Default.Waves, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                // "Done for today" would argue with the "Keep going" button under it.
                text = if (!isCounted) "Nothing kept" else if (firstSession != null) "First session done" else completionTitle(scope),
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = when {
                    // Only about this session: today may count already, from an earlier one.
                    !isCounted -> "You skipped every new sentence, so this session doesn't count as studying."
                    // The first day ends after a minute; "see you tomorrow" would send the user away right then.
                    firstSession != null && keptCount > 0 -> "${plural(keptCount, "new card")} learned. Nice start!"
                    firstSession != null -> "Your first session is done. Nice start!"
                    scope == StudyScope.Daily && keptCount > 0 -> "${plural(keptCount, "new card")} learned. That's today done; see you tomorrow."
                    scope == StudyScope.Daily -> "That's today done; see you tomorrow."
                    againCount == 0 -> "Every card on the first try."
                    else -> "You worked through every card."
                },
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 24.dp)
            )

            if (isCounted) {
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
            }

            if (firstSession != null) FirstSessionEndButtons(firstSession, scope = scope, onBack = onBack)
            else SessionEndButtons(scope = scope, onPracticeAll = onPracticeAll.takeIf { canPracticeAll }, onBack = onBack)
        }
    }
}

/**
 * What the end of the user's first daily session adds to the usual one.
 *
 * @property bonusCount New items a bonus round would bring.
 * @property onKeepGoing Starts the bonus round; null when there is nothing new left to offer (or no way to open one).
 * @property onPickPace Opens the pace sheet.
 */
class FirstSessionEnd(val bonusCount: Int, val onKeepGoing: (() -> Unit)?, val onPickPace: () -> Unit)

/**
 * The end of the first daily session: where the cards are kept (no account, so nobody wonders), a bonus round
 * for someone who wants more on install day, and the pace for someone who wants more every day. "Practice
 * all cards" is left out here: the few cards were just practiced, and the bonus round is the better "more".
 */
@Composable
private fun FirstSessionEndButtons(end: FirstSessionEnd, scope: StudyScope, onBack: () -> Unit) {
    Text(
        text = "Your cards are saved on this phone. No account needed. Back up anytime in Settings › Backup.",
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 20.dp)
    )

    val keepGoing = end.onKeepGoing
    if (keepGoing != null) {
        CoastButton(onClick = keepGoing, modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight)) {
            ButtonLabel("Keep going", plural(end.bonusCount, "more sentence"))
        }
        Spacer(modifier = Modifier.height(12.dp))
        CoastSecondaryButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight)) {
            Text(backLabel(scope))
        }
    } else {
        CoastButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight)) {
            Text(backLabel(scope))
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    TextButton(onClick = end.onPickPace, modifier = Modifier.heightIn(min = 48.dp)) {
        Text("Want more each day? Pick a faster pace", style = MaterialTheme.typography.labelMedium, color = SunlitText, textAlign = TextAlign.Center)
    }
}

/**
 * "Back to islands" and "Practice all cards", shared by the session end screens. Going back is the usual next step.
 *
 * @param onPracticeAll Null leaves "Practice all cards" out, where there is no card to practice.
 */
@Composable
private fun SessionEndButtons(scope: StudyScope, onPracticeAll: (() -> Unit)?, onBack: () -> Unit) {
    CoastButton(
        onClick = onBack,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ButtonHeight)
    ) {
        Text(backLabel(scope))
    }

    if (onPracticeAll != null) {
        Spacer(modifier = Modifier.height(12.dp))

        CoastSecondaryButton(
            onClick = onPracticeAll,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ButtonHeight)
        ) {
            Text("Practice all cards")
        }
    }
}

/** A large [value] with a short [label] below it, for the session summary. */
@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    ContentCard(modifier = modifier, padding = PaddingValues(vertical = 16.dp, horizontal = 8.dp)) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall.tabular,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
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
 * One side of a card as the flip card shows it. A side with several lines (an imported card with a word,
 * its forms and an example) gets room between them, and its first line stands out as the headline; a list
 * of equals ("1. house", "2. home") is set in one size instead.
 *
 * @param language The language the side is written in (its English name), so it is read and drawn as that language.
 * @param onWordClick Makes the words tappable (see [ClickableWordSentence]); null shows plain text.
 */
@Composable
internal fun CardSideText(text: String, language: String = "", onWordClick: ((String) -> Unit)? = null) {
    val lines = remember(text) { text.lines().map { it.trim() }.filter { it.isNotEmpty() } }
    if (lines.size < 2) {
        CardSideLine(text, language, MaterialTheme.typography.headlineLarge, onWordClick)
        return
    }
    val isList = isListItem(lines.first())
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        lines.forEachIndexed { index, line ->
            val style = when {
                isList -> MaterialTheme.typography.headlineSmall
                index == 0 -> MaterialTheme.typography.headlineLarge
                else -> MaterialTheme.typography.titleLarge
            }
            CardSideLine(line, language, style, onWordClick)
        }
    }
}

/** A line of a card's side in [style], with tappable words if [onWordClick] is given. */
@Composable
private fun CardSideLine(text: String, language: String, style: TextStyle, onWordClick: ((String) -> Unit)?) {
    if (onWordClick != null) ClickableWordSentence(sentence = text, onWordClick = onWordClick, style = style, language = language)
    else Text(inLanguage(text, language), style = style)
}

/**
 * Renders a sentence where each word can be tapped. Tappable words get a dotted underline;
 * punctuation stays attached to its word but isn't underlined or part of the lookup.
 *
 * A screen reader gets the sentence as one piece of text in its language, not a stop per word; the flip
 * card offers an action per word in place of the taps (see [lookupWords]). The words keep the order of
 * their own script, whichever way the phone's language is written.
 *
 * @param sentence The full sentence to display.
 * @param onWordClick Callback triggered when a word is clicked, passing the sanitized word.
 *   If null, the words are shown as plain text.
 * @param style The size the words are set in.
 * @param language The language the sentence is written in (its English name).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClickableWordSentence(
    sentence: String,
    onWordClick: ((String) -> Unit)?,
    style: TextStyle = MaterialTheme.typography.headlineLarge,
    language: String = ""
) {
    val tokens = remember(sentence) { sentence.split(' ', '\n', '\t').filter { it.isNotEmpty() }.map(::splitPunctuation) }
    CompositionLocalProvider(LocalLayoutDirection provides sentenceDirection(sentence)) {
        // FlowRow automatically wraps words to the next line
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { text = inLanguage(sentence, language) }
                // The sentence sits on a card that flips when tapped: a tap that just misses a word, in the gap
                // between words or lines, is swallowed here instead of turning the card back over.
                .then(if (onWordClick != null) Modifier.pointerInput(Unit) { detectTapGestures {} } else Modifier),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            // Tappable lines get room between them, so a tap meant for one line doesn't land on the next.
            verticalArrangement = Arrangement.spacedBy(if (onWordClick != null) 8.dp else 2.dp)
        ) {
            tokens.forEach { (leading, word, trailing) ->
                Row {
                    if (leading.isNotEmpty()) WordText(leading, language, style)
                    if (word.isNotEmpty()) {
                        WordText(
                            text = word,
                            language = language,
                            style = style,
                            // Measured after its punctuation, so a word too long for the line wraps inside
                            // itself instead of pushing a trailing comma or full stop out of sight.
                            modifier = Modifier.weight(1f, fill = false).then(
                                if (onWordClick == null || !isLookupWord(word)) Modifier else Modifier
                                    .dottedUnderline(TextSecondary)
                                    .clickable { onWordClick(word) }
                            )
                        )
                    }
                    if (trailing.isNotEmpty()) WordText(trailing, language, style)
                }
            }
        }
    }
}

/** A word or punctuation of a card's sentence, at the size cards are shown in. */
@Composable
private fun WordText(text: String, language: String, style: TextStyle, modifier: Modifier = Modifier) {
    Text(text = inLanguage(text, language), style = style, modifier = modifier)
}

/**
 * The direction [sentence] is written in: right to left when its first letter is from a right-to-left
 * script (Hebrew, Arabic and their neighbours), otherwise left to right.
 */
internal fun sentenceDirection(sentence: String): LayoutDirection {
    val first = sentence.firstOrNull { it.isLetter() }?.code ?: return LayoutDirection.Ltr
    val rightToLeft = first in 0x0590..0x08FF || first in 0xFB1D..0xFDFF || first in 0xFE70..0xFEFF
    return if (rightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr
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
