package com.andreaserick.languagecoast.ui.create

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.andreaserick.languagecoast.data.Coast
import com.andreaserick.languagecoast.data.Flashcard
import com.andreaserick.languagecoast.data.PhraseBank
import com.andreaserick.languagecoast.data.SettingsDefaults
import com.andreaserick.languagecoast.data.DailyPlan
import com.andreaserick.languagecoast.data.Pace
import com.andreaserick.languagecoast.data.daysToLearn
import com.andreaserick.languagecoast.ui.components.PaceSheet
import com.andreaserick.languagecoast.ui.components.rememberGuardedSheetState
import com.andreaserick.languagecoast.ui.components.PlatformBackHandler
import com.andreaserick.languagecoast.ui.components.roughDuration
import com.andreaserick.languagecoast.ui.theme.tabular
import com.andreaserick.languagecoast.data.Language
import com.andreaserick.languagecoast.data.Languages
import com.andreaserick.languagecoast.data.RecentCard
import com.andreaserick.languagecoast.data.TranslationProvider
import com.andreaserick.languagecoast.data.emojiForCategory
import com.andreaserick.languagecoast.data.islandEmoji
import com.andreaserick.languagecoast.data.onDeviceTranslationAvailable
import com.andreaserick.languagecoast.resources.Res
import com.andreaserick.languagecoast.resources.logo_mark
import com.andreaserick.languagecoast.resources.logo_mark_light
import com.andreaserick.languagecoast.ui.theme.isDarkPalette
import com.andreaserick.languagecoast.ui.components.ButtonHeight
import com.andreaserick.languagecoast.ui.components.CardOptionsMenu
import com.andreaserick.languagecoast.ui.components.EditCardSheet
import com.andreaserick.languagecoast.ui.components.LocalUndoMessenger
import com.andreaserick.languagecoast.ui.components.TooltipIconButton
import com.andreaserick.languagecoast.ui.settings.HowToUseSheet
import com.andreaserick.languagecoast.ui.settings.SETTINGS_FOCUS_BACKUP
import com.andreaserick.languagecoast.ui.settings.SETTINGS_FOCUS_TRANSLATION
import com.andreaserick.languagecoast.ui.components.ButtonLabel
import com.andreaserick.languagecoast.ui.components.CoastButton
import com.andreaserick.languagecoast.ui.components.CoastSecondaryButton
import com.andreaserick.languagecoast.ui.components.CoastTextField
import com.andreaserick.languagecoast.ui.components.ContentCard
import com.andreaserick.languagecoast.ui.components.FeedbackKind
import com.andreaserick.languagecoast.ui.components.IconTile
import com.andreaserick.languagecoast.ui.components.KeepScreenOn
import com.andreaserick.languagecoast.ui.components.LocalReducedMotion
import com.andreaserick.languagecoast.ui.components.LanguagePickerDialog
import com.andreaserick.languagecoast.ui.components.LocalFeedback
import com.andreaserick.languagecoast.ui.components.Pill
import com.andreaserick.languagecoast.ui.components.ScreenHeader
import com.andreaserick.languagecoast.ui.components.ScreenMargin
import com.andreaserick.languagecoast.ui.components.SegmentedSwitch
import com.andreaserick.languagecoast.ui.components.TileEmoji
import com.andreaserick.languagecoast.ui.components.collapseExit
import com.andreaserick.languagecoast.ui.components.expandEnter
import com.andreaserick.languagecoast.ui.components.coastChipBorder
import com.andreaserick.languagecoast.ui.components.coastChipColors
import com.andreaserick.languagecoast.ui.components.inLanguage
import com.andreaserick.languagecoast.ui.components.plural
import com.andreaserick.languagecoast.ui.theme.CoralText
import com.andreaserick.languagecoast.ui.theme.OnSunlit
import com.andreaserick.languagecoast.ui.theme.Seafoam
import com.andreaserick.languagecoast.ui.theme.SurfaceRaised
import com.andreaserick.languagecoast.ui.theme.SurfaceSelected
import com.andreaserick.languagecoast.ui.theme.Sunlit
import com.andreaserick.languagecoast.ui.theme.SunlitText
import com.andreaserick.languagecoast.ui.theme.TextPrimary
import com.andreaserick.languagecoast.ui.theme.TextSecondary
import com.andreaserick.languagecoast.data.RouteLevel
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The "Today" screen, the app's home: the focus coast with why the user learns it and one button that
 * starts today's session (a few due cards, then a few new sentences prepared for that goal). Below it,
 * folded away, the form for a card of the user's own: translated by the provider picked in Settings,
 * written in the coast's language and checked by the AI, or typed by hand. Whole paragraphs and photos
 * can be captured as a batch of cards.
 */
@Composable
fun CreateScreen(
    onStartDaily: (coastId: Int, language: String) -> Unit,
    onReviewCoast: (coastId: Int, coastName: String) -> Unit,
    onReviewAll: () -> Unit,
    onAboutYou: () -> Unit,
    onOpenSettings: (focus: String) -> Unit,
    onSearch: () -> Unit,
    viewModel: CreateViewModel = koinViewModel()
) {
    // The app can sit in the background over midnight: the day and the due counts are taken again on the way back.
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose {}
    }
    val undoMessenger = LocalUndoMessenger.current
    // Kept as the card's id, so the open sheet survives a rotation; the card itself is looked up in the list.
    var editingCardId by rememberSaveable { mutableStateOf<Int?>(null) }
    val state = viewModel.uiState
    state.recentCards.firstOrNull { it.card.cardId == editingCardId }?.let { recent ->
        EditCardSheet(
            card = recent.card,
            islands = state.coastIslands,
            nativeLanguage = state.nativeLanguage,
            targetLanguage = state.selectedCoast?.language.orEmpty(),
            onSave = { edit ->
                viewModel.editCard(recent.card.cardId, edit)
                editingCardId = null
            },
            onDismiss = { editingCardId = null }
        )
    }
    CreateContent(
        uiState = viewModel.uiState,
        onNativeSentenceChange = viewModel::onNativeSentenceChange,
        onTargetSentenceChange = viewModel::onTargetSentenceChange,
        onCategoryChange = viewModel::onCategoryChange,
        onInputModeChange = viewModel::onInputModeChange,
        onCoastSelected = viewModel::onCoastSelected,
        onAddCoast = viewModel::addCoast,
        onNativeLanguage = viewModel::setNativeLanguage,
        onSave = viewModel::save,
        onRetryWithFallback = viewModel::retryWithFallback,
        onUndo = viewModel::undoLastSave,
        onEditSavedTranslation = viewModel::updateSavedTranslation,
        onPauseResultDismissal = viewModel::pauseResultDismissal,
        onResumeResultDismissal = viewModel::resumeResultDismissal,
        onStartBatch = viewModel::startBatch,
        onToggleBatchSentence = viewModel::toggleBatchSentence,
        onSaveBatch = viewModel::saveBatch,
        onDismissBatch = viewModel::dismissBatch,
        onAddStarters = viewModel::addStarterSentences,
        onEditGoal = viewModel::editGoal,
        onSetGoal = viewModel::setGoal,
        onDismissGoalPrompt = viewModel::dismissGoalPrompt,
        onStartDaily = onStartDaily,
        onReviewCoast = onReviewCoast,
        onReviewAll = onReviewAll,
        onSetPace = viewModel::setPace,
        onAboutYou = onAboutYou,
        onDismissAboutYou = viewModel::dismissAboutYouInvite,
        onRetrySuggestions = viewModel::retrySuggestions,
        onOpenSettings = onOpenSettings,
        onSearch = onSearch,
        onDismissGettingStarted = viewModel::dismissGettingStarted,
        onEditRecent = { editingCardId = it.cardId },
        onDeleteRecent = { card ->
            viewModel.deleteCard(card) { deleted -> undoMessenger.show("Card deleted") { viewModel.restore(deleted) } }
        }
    )
}

/** Pasted text longer than this isn't kept across a rotation: saved state has a size limit, and going over it crashes the app. */
private const val MAX_SAVED_PASTE_LENGTH = 20_000

/** Keeps the text pasted into "Add many cards" across a rotation, unless it is very long (see [MAX_SAVED_PASTE_LENGTH]). */
private val PastedTextSaver = Saver<String, String>(
    save = { text -> if (text.length <= MAX_SAVED_PASTE_LENGTH) text else "" },
    restore = { it }
)

/**
 * The home screen's content: onboarding until there is a coast, otherwise today's session on the focus
 * coast, the folded-away new card form, the result of the last save and the most recently added cards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateContent(
    uiState: CreateUiState,
    onNativeSentenceChange: (String) -> Unit,
    onTargetSentenceChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onInputModeChange: (InputMode) -> Unit,
    onCoastSelected: (Coast) -> Unit,
    onAddCoast: (String) -> Unit,
    onNativeLanguage: (String) -> Unit,
    onSave: () -> Unit,
    onRetryWithFallback: () -> Unit,
    onUndo: () -> Unit,
    onEditSavedTranslation: (String) -> Unit,
    onPauseResultDismissal: () -> Unit,
    onResumeResultDismissal: () -> Unit,
    onStartBatch: (String) -> Unit,
    onToggleBatchSentence: (Int) -> Unit,
    onSaveBatch: () -> Unit,
    onDismissBatch: () -> Unit,
    onAddStarters: (List<StarterSentence>) -> Unit,
    onEditGoal: (Coast) -> Unit,
    onSetGoal: (Coast, String, String) -> Unit,
    onDismissGoalPrompt: () -> Unit,
    onStartDaily: (coastId: Int, language: String) -> Unit,
    onReviewCoast: (coastId: Int, coastName: String) -> Unit,
    onReviewAll: () -> Unit,
    onSetPace: (Pace) -> Unit,
    onAboutYou: () -> Unit,
    onDismissAboutYou: () -> Unit,
    onRetrySuggestions: () -> Unit,
    onOpenSettings: (focus: String) -> Unit,
    onSearch: () -> Unit,
    onDismissGettingStarted: () -> Unit,
    onEditRecent: (Flashcard) -> Unit,
    onDeleteRecent: (Flashcard) -> Unit
) {
    // Open sheets and dialogs are saved, so turning or unfolding the phone doesn't close them.
    var showNewCoastDialog by rememberSaveable { mutableStateOf(false) }
    var showPace by rememberSaveable { mutableStateOf(false) }
    if (showPace) {
        PaceSheet(pace = uiState.pace, waiting = uiState.plan?.newOnCoast ?: 0, onPick = onSetPace, onDismiss = { showPace = false })
    }
    var showBatchSheet by rememberSaveable { mutableStateOf(false) }
    var showHowToUse by rememberSaveable { mutableStateOf(false) }
    if (showHowToUse) HowToUseSheet(onDismiss = { showHowToUse = false })
    // The form is folded away: the day starts with the session, and a sentence of your own is the exception.
    var composerOpen by rememberSaveable { mutableStateOf(false) }

    // The photo reader lives here, not in the sheet: the form's link says whether photos can be read at all,
    // and a picked photo finds its way back even when the screen was rebuilt while the picker was open.
    var batchText by rememberSaveable(stateSaver = PastedTextSaver) { mutableStateOf("") }
    // Not saved: a read cut off by a rotation must not leave the sheet "reading" for good.
    var readingPhoto by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf<String?>(null) }
    val photoReader = rememberPhotoTextReader(
        onText = {
            batchText = it
            photoError = null
        },
        onError = { photoError = it },
        onReading = { readingPhoto = it }
    )
    // The language a batch is written in. The reader only knows Latin letters, so photos are offered for those languages only.
    val batchLanguage = if (uiState.inputMode == InputMode.WriteInTarget) uiState.selectedCoast?.language.orEmpty() else uiState.nativeLanguage
    val readPhoto = photoReader?.takeIf { Languages.isLatinScript(batchLanguage) }

    uiState.goalPrompt?.let { coast ->
        // Keyed by the coast, so a goal typed for one coast is never restored into the sheet of another.
        key(coast.coastId) {
            // The coast just started during onboarding: its goal is the last of the first steps.
            val isFirstCoast = uiState.coasts.size == 1 && coast.goal == null && (uiState.focusSummary?.cardCount ?: 0) == 0
            GoalSheet(
                coast = coast,
                nativeLanguage = uiState.nativeLanguage,
                isOnboarding = isFirstCoast,
                onSave = { goal, region -> onSetGoal(coast, goal, region) },
                onDismiss = onDismissGoalPrompt
            )
        }
    }

    var showStarters by rememberSaveable { mutableStateOf(false) }
    val starterCoast = uiState.selectedCoast
    if (showStarters && starterCoast != null) {
        StarterSentencesSheet(
            coastLanguage = starterCoast.language,
            nativeLanguage = uiState.nativeLanguage,
            coastSentences = uiState.coastSentences,
            profile = uiState.profile,
            onAdd = { picked ->
                showStarters = false
                onAddStarters(picked)
            },
            onDismiss = { showStarters = false }
        )
    }

    if (showNewCoastDialog) {
        LanguagePickerDialog(
            title = "New coast",
            subtitle = "Which language do you want to study?",
            languages = uiState.availableLanguages,
            onPick = { language ->
                onAddCoast(language.name)
                showNewCoastDialog = false
            },
            onDismiss = { showNewCoastDialog = false }
        )
    }

    val batch = uiState.batch
    if (showBatchSheet || batch != null) {
        BatchSheet(
            batch = batch,
            writing = batchLanguage,
            text = batchText,
            onTextChange = { batchText = it },
            readPhoto = readPhoto,
            readingPhoto = readingPhoto,
            photoError = photoError,
            onStartBatch = onStartBatch,
            onToggle = onToggleBatchSentence,
            onSave = onSaveBatch,
            // Drops the empty split but keeps the sheet and the text, so it can be fixed and split again.
            onEditText = {
                showBatchSheet = true
                onDismissBatch()
            },
            onDismiss = {
                showBatchSheet = false
                // A stray swipe keeps the pasted text for next time; only a finished batch clears it.
                if (batch?.isDone == true) batchText = ""
                photoError = null
                onDismissBatch()
            }
        )
    }
    // A long batch takes minutes and stops when the phone sleeps.
    if (batch?.isSaving == true) KeepScreenOn()

    Column(modifier = Modifier.fillMaxSize()) {
        // The tab is called Today; only the welcome before the first coast carries the app's name.
        val welcoming = !uiState.isLoadingCoasts && uiState.selectedCoast == null
        ScreenHeader(
            title = if (welcoming) "Language Coast" else "Today",
            leading = {
                // The mark sits in the middle of its canvas, so it's drawn larger than it looks and pulled left.
                Image(
                    painter = painterResource(if (isDarkPalette) Res.drawable.logo_mark else Res.drawable.logo_mark_light),
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .offset(x = (-8).dp)
                )
            },
            actions = {
                if (uiState.streakCount > 0) StreakBadge(uiState.streakCount)
                // Finding a card is a job of every day, so it is here as well as on My Coasts.
                if (uiState.selectedCoast != null) TooltipIconButton(icon = Icons.Default.Search, label = "Search cards", onClick = onSearch)
            }
        )

        val selectedCoast = uiState.selectedCoast
        when {
            // Show nothing while loading to avoid flashing the welcome.
            uiState.isLoadingCoasts -> Unit
            selectedCoast == null -> Onboarding(
                nativeLanguage = uiState.nativeLanguage,
                availableLanguages = uiState.availableLanguages,
                onNativeLanguage = onNativeLanguage,
                onStartCoast = { showNewCoastDialog = true }
            )
            else -> Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ScreenMargin, vertical = 12.dp)
            ) {
                DailyCard(
                    coast = selectedCoast,
                    coasts = uiState.coasts,
                    plan = uiState.plan,
                    doneToday = uiState.doneToday,
                    coastHasCards = (uiState.focusSummary?.cardCount ?: 0) > 0,
                    isPreparing = uiState.isPreparingSentences,
                    dueEverywhere = uiState.dueCount,
                    onCoastSelected = onCoastSelected,
                    onEditGoal = { onEditGoal(selectedCoast) },
                    onStart = { onStartDaily(selectedCoast.coastId, selectedCoast.language) },
                    onReviewCoast = { onReviewCoast(selectedCoast.coastId, selectedCoast.displayName) },
                    onReviewAll = onReviewAll,
                    onBrowseStarters = { showStarters = true },
                    onAddOwn = { composerOpen = true },
                    pace = uiState.pace,
                    onChangePace = { showPace = true },
                    level = uiState.level,
                    provider = uiState.translationProvider,
                    engineReady = uiState.engineReady,
                    awaitingPermission = uiState.awaitingPermission,
                    suggestionError = uiState.suggestionError,
                    onRetrySuggestions = onRetrySuggestions,
                    onSetUpTranslation = { onOpenSettings(SETTINGS_TRANSLATION) }
                )

                uiState.gettingStarted?.let { steps ->
                    GettingStartedCard(
                        steps = steps,
                        onSetUpTranslation = { onOpenSettings(SETTINGS_TRANSLATION) },
                        onBackUp = { onOpenSettings(SETTINGS_BACKUP) },
                        onHowItWorks = { showHowToUse = true },
                        onHide = onDismissGettingStarted
                    )
                }

                // The islands it leads to are written by an AI model, so it is only offered where one can write them.
                if (uiState.showAboutYouInvite && uiState.engineReady && uiState.translationProvider != TranslationProvider.OnDevice) {
                    AboutYouInvite(onOpen = onAboutYou, onDismiss = onDismissAboutYou)
                }

                ComposerHeader(open = composerOpen, onToggle = { composerOpen = !composerOpen })
                AnimatedVisibility(
                    visible = composerOpen,
                    enter = expandEnter(),
                    exit = collapseExit()
                ) {
                    NewCardForm(
                        uiState = uiState,
                        selectedCoast = selectedCoast,
                        onNativeSentenceChange = onNativeSentenceChange,
                        onTargetSentenceChange = onTargetSentenceChange,
                        onCategoryChange = onCategoryChange,
                        onInputModeChange = onInputModeChange,
                        onSave = onSave,
                        onAddMany = { showBatchSheet = true },
                        onBrowseStarters = { showStarters = true },
                        onSetUpTranslation = { onOpenSettings(SETTINGS_TRANSLATION) },
                        photoOffered = readPhoto != null
                    )
                }

                // The preview appears below the form, often off screen; scroll to it once it has expanded.
                val resultRequester = remember { BringIntoViewRequester() }
                val feedback = LocalFeedback.current
                // With reduced motion the preview appears at full size at once, so there is nothing to wait for.
                val reducedMotion = LocalReducedMotion.current
                LaunchedEffect(uiState.result) {
                    when (uiState.result) {
                        is SaveResult.Saved -> feedback.play(FeedbackKind.Success)
                        is SaveResult.Error -> feedback.play(FeedbackKind.Failure)
                        null -> return@LaunchedEffect
                    }
                    if (!reducedMotion) delay(RESULT_SCROLL_DELAY_MILLIS)
                    resultRequester.bringIntoView()
                }
                AnimatedVisibility(
                    visible = uiState.result != null,
                    enter = expandEnter(),
                    exit = collapseExit(),
                    modifier = Modifier.bringIntoViewRequester(resultRequester)
                ) {
                    when (val result = uiState.result) {
                        // Keyed by the card, so what was typed into one card's preview is never restored into the next one's.
                        is SaveResult.Saved -> key(result.added.card.cardId) {
                            SavedCard(
                                result = result,
                                language = selectedCoast.language,
                                onUndo = onUndo,
                                onEditTranslation = onEditSavedTranslation,
                                onStartEditing = onPauseResultDismissal,
                                onCancelEditing = onResumeResultDismissal
                            )
                        }
                        is SaveResult.Error -> ErrorCard(
                            message = result.message,
                            fallback = uiState.fallbackProvider,
                            onRetry = if (uiState.fallbackProvider != null) onRetryWithFallback else onSave
                        )
                        null -> Unit
                    }
                }

                if (uiState.recentCards.isNotEmpty()) {
                    RecentCards(uiState.recentCards, language = selectedCoast.language, onEdit = onEditRecent, onDelete = onDeleteRecent)
                }
            }
        }
    }
}

/** What Settings is opened at from Today (see [SettingsScreen]'s `focus`). */
private const val SETTINGS_TRANSLATION = SETTINGS_FOCUS_TRANSLATION
private const val SETTINGS_BACKUP = SETTINGS_FOCUS_BACKUP

/** Long enough for the save preview's expand animation to finish before scrolling to it. */
private const val RESULT_SCROLL_DELAY_MILLIS = 350L

/** The streak shown in the header, e.g. "4 days". */
@Composable
private fun StreakBadge(streak: Int) {
    Text(
        text = plural(streak, "day"),
        style = MaterialTheme.typography.labelMedium.tabular,
        color = SunlitText,
        maxLines = 1,
        modifier = Modifier
            .padding(start = 8.dp, end = 12.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${plural(streak, "day")} streak" }
    )
}

/**
 * The heart of the Today screen: the focus coast, why the user learns it, and one button that starts
 * today's session. The due pile is deliberately not the headline; what the session leaves over waits
 * for another day.
 *
 * @param plan Today's session, or null before the coast's counts are known.
 * @param doneToday A session was already completed today; another round is only offered as a link.
 * @param coastHasCards Whether the coast has any cards, which tells an empty day from an empty coast.
 * @param isPreparing Whether new sentences for the coast are being made right now.
 * @param dueEverywhere Due cards on every coast together, offered as a link when other coasts have some.
 * @param provider The provider picked in Settings.
 * @param engineReady Whether [provider] can translate right now. If not, the card says what it still needs
 *     and where to give it, and starter sentences aren't offered: they would only fail to translate.
 * @param awaitingPermission [provider], while all it lacks is the permission to be sent text.
 * @param suggestionError Why no new sentences could be prepared, when the last attempt failed.
 * @param onSetUpTranslation Opens Settings at Translation, where the provider gets its key or permission.
 */
@Composable
private fun DailyCard(
    coast: Coast,
    coasts: List<Coast>,
    plan: DailyPlan?,
    doneToday: Boolean,
    coastHasCards: Boolean,
    isPreparing: Boolean,
    dueEverywhere: Int,
    onCoastSelected: (Coast) -> Unit,
    onEditGoal: () -> Unit,
    onStart: () -> Unit,
    onReviewCoast: () -> Unit,
    onReviewAll: () -> Unit,
    onBrowseStarters: () -> Unit,
    onAddOwn: () -> Unit,
    pace: Pace,
    onChangePace: () -> Unit,
    level: RouteLevel?,
    provider: TranslationProvider,
    engineReady: Boolean,
    awaitingPermission: TranslationProvider?,
    suggestionError: String?,
    onRetrySuggestions: () -> Unit,
    onSetUpTranslation: () -> Unit
) {
    val language = Languages.byName(coast.language)
    ContentCard(padding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(size = 40.dp) { TileEmoji(language?.flag ?: "🌍", tileSize = 40.dp) }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = coast.region?.let { "${coast.language} · $it" } ?: coast.language,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                GoalLine(goal = coast.goal, onClick = onEditGoal)
            }
            if (coasts.size > 1) CoastPicker(coasts = coasts, selected = coast, onSelected = onCoastSelected)
        }
        if (level == null) {
            HorizontalDivider(color = SurfaceRaised, modifier = Modifier.padding(vertical = 14.dp))
        } else {
            // The line under the coast is the level's progress: where today's session is taking the user.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                Pill(level.level.shortLabel, container = Sunlit, content = OnSunlit)
                Spacer(modifier = Modifier.width(10.dp))
                LinearProgressIndicator(
                    progress = { level.progress },
                    color = Sunlit,
                    trackColor = SurfaceRaised,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                    modifier = Modifier.weight(1f).height(6.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("${(level.progress * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium.tabular, color = TextSecondary)
            }
        }

        when {
            plan == null -> Unit
            doneToday -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Seafoam, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Done for today", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = "You've studied today. The next session is ready tomorrow.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                if (!plan.isEmpty) {
                    // A little padding keeps the first and last glyphs clear of the button's clip; the offset lines the text up with the card's text.
                    TextButton(
                        onClick = onStart,
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = SunlitText),
                        modifier = Modifier.offset(x = (-6).dp)
                    ) {
                        // The label's parts take the button's text style and colour, as a plain Text in it did before.
                        ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                            ButtonLabel(
                                action = "Another round",
                                detail = listOfNotNull(
                                    plan.reviewCount.takeIf { it > 0 }?.let { "$it to review" },
                                    plan.newCount.takeIf { it > 0 }?.let { "$it new" }
                                ).joinToString(", ")
                            )
                        }
                    }
                }
            }
            !plan.isEmpty -> {
                Text(
                    text = if (plan.isWelcomeBack) "Welcome back" else "Today's session",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (plan.isWelcomeBack) "Just a small one to get going: ${sessionSummary(plan)}." else sessionSummary(plan),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                CoastButton(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight)) {
                    if (plan.isWelcomeBack) Text("Ease back in") else ButtonLabel("Start", "about ${plural(plan.minutes, "minute")}")
                }
                if (plan.leftOver > 0) LeftOverLine(leftOver = plan.leftOver, dueOnCoast = plan.dueOnCoast, onReviewAll = onReviewCoast)
                WaitingLine(waiting = plan.newOnCoast, pace = pace, onChangePace = onChangePace)
            }
            isPreparing -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = SunlitText, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = when {
                            coastHasCards -> "Nothing due. Preparing new sentences…"
                            // The first translation on the phone fetches a language pack, on whatever network there is; better said than not.
                            provider == TranslationProvider.OnDevice ->
                                "Getting your first sentences ready. The first time, the phone downloads its ${coast.language} translator " +
                                    "(about 30 MB), which usually takes under a minute on Wi-Fi. Meanwhile, browse the starter sentences."
                            else -> "Getting your first sentences ready. This usually takes under a minute."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
            coastHasCards -> {
                Text("All caught up", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (engineReady) "Nothing due today and no new sentences ready. Add one of your own, or come back tomorrow."
                    else "Nothing due today. " + setUpText(provider, awaitingPermission),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                if (engineReady && suggestionError != null) {
                    SuggestionProblem(suggestionError, onRetry = onRetrySuggestions)
                    // Two actions one above the other need a gap, or a tap meant for one lands on the other.
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (!engineReady) {
                    TextButton(onClick = onSetUpTranslation, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Text(setUpAction(awaitingPermission), style = MaterialTheme.typography.labelMedium, color = SunlitText)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                TextButton(onClick = onReviewCoast, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text("Practice anyway", style = MaterialTheme.typography.labelMedium, color = SunlitText)
                }
            }
            !engineReady -> {
                Text("One quick step before your first sentences", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = setUpText(provider, awaitingPermission),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            else -> {
                Text("Nothing here yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Start with the sentences everyone needs in ${coast.language}, or add one of your own." +
                        if (provider == TranslationProvider.OnDevice) " An AI provider in Settings can suggest sentences for your goal too." else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                if (suggestionError != null) SuggestionProblem(suggestionError, onRetry = onRetrySuggestions)
            }
        }
        if (plan != null && plan.isEmpty && !doneToday) {
            Spacer(modifier = Modifier.height(8.dp))
            if (engineReady) {
                CoastSecondaryButton(onClick = onBrowseStarters, modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight)) {
                    Text("Browse starter sentences")
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onAddOwn, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Or add your own", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                }
            } else {
                // Starter sentences need translating, so the way in is setting up translation, or a card typed by hand meanwhile.
                CoastButton(onClick = onSetUpTranslation, modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight)) {
                    if (awaitingPermission != null) Text(setUpAction(awaitingPermission)) else ButtonLabel(setUpAction(null), "about 2 minutes")
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onAddOwn, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Or type a card yourself", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                }
            }
        }
        if (plan != null && dueEverywhere > plan.dueOnCoast) {
            // It usually sits under another action (a button or a tappable line), so it keeps the same gap from it.
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onReviewAll, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text("${plural(dueEverywhere, "card")} due across all coasts", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }
        }
    }
}

/**
 * What translation still needs before it works, in plain words and with how long it takes. The button next
 * to it ([setUpAction]) opens Settings right where it is given, so no path through the menus is named.
 */
private fun setUpText(provider: TranslationProvider, awaitingPermission: TranslationProvider?): String = when {
    awaitingPermission != null ->
        "Language Coast needs your permission before it sends text to ${awaitingPermission.label}. It takes one tap."
    // Only where the phone can translate by itself is that a way out worth naming.
    onDeviceTranslationAvailable ->
        "${provider.label} needs a key before it can translate. Setting one up takes about 2 minutes, " +
            "or switch to ${TranslationProvider.OnDevice.label}, which translates on this phone for free."
    provider == TranslationProvider.Gemini ->
        "Language Coast translates with Google Gemini. It's free with your own Gemini key, and getting one takes about 2 minutes."
    else -> "Language Coast translates with ${provider.label}, using your own key. Setting it up takes about 2 minutes."
}

/** The label of the button that opens Settings at Translation. */
private fun setUpAction(awaitingPermission: TranslationProvider?): String =
    if (awaitingPermission != null) "Allow ${awaitingPermission.label}" else "Set up translation"

/** Why no new sentences could be prepared, with a way to have it tried again. */
@Composable
private fun SuggestionProblem(reason: String, onRetry: () -> Unit) {
    Text(
        text = reason,
        style = MaterialTheme.typography.bodySmall,
        color = CoralText,
        modifier = Modifier
            .padding(top = 6.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
    )
    TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 0.dp)) {
        Text("Try again", style = MaterialTheme.typography.labelMedium, color = SunlitText)
    }
}

/**
 * What the capped session leaves behind, as one quiet sentence under the Start button: "56 more cards can
 * wait for another day, or review all 68." The line reads as one thought and wraps like prose on narrow
 * screens; the accent words only show where the tap leads.
 */
@Composable
private fun LeftOverLine(leftOver: Int, dueOnCoast: Int, onReviewAll: () -> Unit) {
    val text = buildAnnotatedString {
        append("${plural(leftOver, "more card")} can wait for another day, or ")
        withStyle(SpanStyle(color = SunlitText, fontWeight = FontWeight.SemiBold)) { append("review all $dueOnCoast") }
        append(".")
    }
    TappableLine(text = text, onClickLabel = "review all $dueOnCoast", onClick = onReviewAll)
}

/**
 * The pace, under the Start button: with new cards waiting on the coast, how long they take at this pace
 * ("212 new cards are waiting: about 10 weeks at the steady pace. Go faster."); otherwise just the pace
 * and the way to change it. Tapped as a whole, like [LeftOverLine].
 */
@Composable
private fun WaitingLine(waiting: Int, pace: Pace, onChangePace: () -> Unit) {
    val text = buildAnnotatedString {
        if (waiting > 0) {
            append("${plural(waiting, "new card")} ${if (waiting == 1) "is" else "are"} waiting: ${roughDuration(daysToLearn(waiting, pace))} at the ${pace.label.lowercase()} pace. ")
        } else {
            append("${pace.label} pace, up to ${pace.newPerDay} new a day. ")
        }
        withStyle(SpanStyle(color = SunlitText, fontWeight = FontWeight.SemiBold)) {
            append(if (pace == Pace.Sprint) "Change pace" else if (waiting > 0) "Go faster" else "Change pace")
        }
        append(".")
    }
    TappableLine(text = text, onClickLabel = "change the pace", onClick = onChangePace)
}

/**
 * A quiet line of text with one action in it. A link of a few small words was too small to hit, so the
 * whole line is the target, at least 48 dp tall; the gap above keeps it apart from the action before it.
 */
@Composable
private fun TappableLine(text: AnnotatedString, onClickLabel: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

/**
 * An invitation, until the user answers a first question or waves it away: the app can only write islands
 * about their life once it knows something about it.
 */
@Composable
private fun AboutYouInvite(onOpen: () -> Unit, onDismiss: () -> Unit) {
    ContentCard(padding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            IconTile(size = 40.dp) { TileEmoji("🪪", tileSize = 40.dp) }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Make it about you", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Most of what you'll say is about your own life. Answer a few questions and your coast gets islands about your family, your work and your free time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onDismiss) { Text("Not now", style = MaterialTheme.typography.labelMedium, color = TextSecondary) }
            TextButton(onClick = onOpen) { Text("Answer a few questions", style = MaterialTheme.typography.labelMedium, color = SunlitText) }
        }
    }
}

/** "8 cards to review · 5 new cards · 3 new sentences", leaving out whatever is zero. */
private fun sessionSummary(plan: DailyPlan): String = listOfNotNull(
    plan.reviewCount.takeIf { it > 0 }?.let { "${plural(it, "card")} to review" },
    plan.newCardCount.takeIf { it > 0 }?.let { plural(it, "new card") },
    plan.newSentenceCount.takeIf { it > 0 }?.let { plural(it, "new sentence") }
).joinToString(" · ")

/** Why the user learns the coast's language, or an invitation to say; tapping opens the [GoalSheet]. */
@Composable
private fun GoalLine(goal: String?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = if (goal == null) null else "change the goal", role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = goal?.let { "Goal: $it" } ?: "Set a goal" }
            .padding(vertical = 8.dp)
    ) {
        Icon(Icons.Default.Flag, contentDescription = null, tint = SunlitText, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = goal ?: "Why are you learning it? Tap to say",
            style = MaterialTheme.typography.bodySmall,
            color = if (goal == null) SunlitText else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Starting points for a goal; tapping one puts it in the field to finish. */
private val GOAL_EXAMPLES = listOf("A trip to ", "Talking with ", "Work", "A show I watch", "Just for fun")

/**
 * Asks why the user learns [coast]'s language. The answer steers the sentences the daily session offers,
 * which is the whole point of asking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalSheet(
    coast: Coast,
    nativeLanguage: String,
    isOnboarding: Boolean,
    onSave: (goal: String, region: String) -> Unit,
    onDismiss: () -> Unit
) {
    // Saved, so what was typed survives turning the phone.
    var text by rememberSaveable(coast.coastId) { mutableStateOf(coast.goal.orEmpty()) }
    var region by rememberSaveable(coast.coastId) { mutableStateOf(coast.region.orEmpty()) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val isDirty = text != coast.goal.orEmpty() || region != coast.region.orEmpty()
    fun requestDismiss() {
        if (isDirty) confirmDiscard = true else onDismiss()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberGuardedSheetState(guarded = isDirty, onBlocked = { confirmDiscard = true })
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            if (isOnboarding) Text("Step 3 of 3", style = MaterialTheme.typography.labelMedium, color = SunlitText)
            Text("Why ${coast.language}?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(
                text = "A trip, a person, a job, a show you love. Each day's new sentences are picked for it, so the more specific the better.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            CoastTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Your reason") },
                placeholder = { Text("e.g. A trip to Lisbon in March") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            ) {
                GOAL_EXAMPLES.forEach { example ->
                    FilterChip(
                        selected = false,
                        onClick = { text = example },
                        label = { Text(example.trim(), style = MaterialTheme.typography.labelMedium) },
                        colors = coastChipColors(),
                        border = coastChipBorder(enabled = true, selected = false),
                        shape = MaterialTheme.shapes.small
                    )
                }
            }
            GoalPreview(goal = text, coastLanguage = coast.language, nativeLanguage = nativeLanguage)
            CoastTextField(
                value = region,
                onValueChange = { region = it },
                label = { Text("Where will you use it? (optional)") },
                placeholder = { Text("e.g. Mexico, Brazil, Switzerland") },
                supportingText = { Text("Translations use that place's words, forms and register.", color = TextSecondary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            CoastButton(onClick = { onSave(text, region) }, modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight)) {
                Text("Save")
            }
            TextButton(onClick = ::requestDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(if (coast.goal == null) "Not now" else "Cancel", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your changes to this coast's goal haven't been saved yet.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onDismiss()
                }) { Text("Discard", color = CoralText, style = MaterialTheme.typography.labelMedium) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text("Keep editing", color = TextPrimary, style = MaterialTheme.typography.labelMedium)
                }
            }
        )
    }
}

/**
 * What the goal being typed changes, while it is typed: the topic the first new sentences come from, with one
 * of them. The starter sentences are written in English, so for anyone else only the topic is shown.
 */
@Composable
private fun GoalPreview(goal: String, coastLanguage: String, nativeLanguage: String) {
    val preview = remember(goal, coastLanguage) { PhraseBank.goalPreview(goal, mapOf(PhraseBank.LANGUAGE_PLACEHOLDER to coastLanguage)) }
    AnimatedVisibility(visible = preview != null, enter = expandEnter(), exit = collapseExit()) {
        // Kept while it folds away, so the card doesn't empty before it is gone.
        val shown = remember { mutableStateOf(preview) }
        if (preview != null) shown.value = preview
        val (theme, sentence) = shown.value ?: return@AnimatedVisibility
        ContentCard(
            padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
        ) {
            Text("Picked for this goal", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Text("${theme.emoji} ${theme.category}", style = MaterialTheme.typography.titleSmall)
            if (nativeLanguage == PHRASE_BANK_LANGUAGE) {
                Text("e.g. \u201C$sentence\u201D", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
    }
}

/** The language the starter sentences are written in. */
private const val PHRASE_BANK_LANGUAGE = "English"

/** The fold of the new card form: a title that opens and closes it. */
@Composable
private fun ComposerHeader(open: Boolean, onToggle: () -> Unit) {
    // At least a finger tall even when open, when only the title's line is left.
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = if (open) "hide the form" else "show the form", role = Role.Button, onClick = onToggle)
            .padding(top = 4.dp)
    ) {
        // The chevron sits on the title's line, not centred on title and subtitle together, so it reads with the title.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Add your own", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
            // What a tap does is said by the row's click label, so the chevron itself stays silent.
            Icon(
                imageVector = if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = TextSecondary
            )
        }
        if (!open) {
            Text("Something you wanted to say today", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

/**
 * The form for a new card on [selectedCoast]: the sentence, and either a translation typed by hand
 * or an optional category for the automatic translation.
 */
@Composable
private fun NewCardForm(
    uiState: CreateUiState,
    selectedCoast: Coast,
    onNativeSentenceChange: (String) -> Unit,
    onTargetSentenceChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onInputModeChange: (InputMode) -> Unit,
    onSave: () -> Unit,
    onAddMany: () -> Unit,
    onBrowseStarters: () -> Unit,
    onSetUpTranslation: () -> Unit,
    photoOffered: Boolean
) {
    val mode = uiState.inputMode
    // The picked provider can't translate yet (no key, or no permission), and this mode needs it to.
    val needsSetUp = mode != InputMode.Manual && !uiState.engineReady
    val awaitingPermission = uiState.awaitingPermission
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        if (uiState.isSameLanguage) {
            Text(
                text = sameLanguageMessage(selectedCoast.language),
                color = CoralText,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        SegmentedSwitch(
            // Named by what the user does: have it translated, write it in the coast's language and have it corrected, or type both sides.
            options = listOf("Translate", "Correct mine", "Type both"),
            selectedIndex = mode.ordinal,
            onSelect = { onInputModeChange(InputMode.entries[it]) },
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = when {
                needsSetUp && awaitingPermission != null ->
                    "Language Coast needs your permission to send text to ${awaitingPermission.label} first, or switch to Type both."
                needsSetUp -> "${uiState.translationProvider.label} isn't set up yet. Set it up first, or switch to Type both."
                mode == InputMode.Translate -> "${uiState.translationProvider.label} translates it: ${uiState.nativeLanguage} → ${selectedCoast.language}"
                mode == InputMode.WriteInTarget -> "Write in ${selectedCoast.language}. ${uiState.translationProvider.label} fills in the ${uiState.nativeLanguage} and corrects slips."
                else -> "You type both sides yourself."
            },
            color = if (needsSetUp) CoralText else TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        if (needsSetUp) {
            TextButton(onClick = onSetUpTranslation, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text(setUpAction(awaitingPermission), style = MaterialTheme.typography.labelMedium, color = SunlitText)
            }
        }

        CoastTextField(
            value = uiState.nativeSentence,
            onValueChange = onNativeSentenceChange,
            label = { Text(if (mode == InputMode.WriteInTarget) "Write it in ${selectedCoast.language}" else "What do you want to say?") },
            placeholder = { Text(if (mode == InputMode.WriteInTarget) "e.g. Wo ist der Bahnhof?" else "e.g. Where is the train station?") },
            // Only a hint: the user may want a second card, e.g. on another island.
            supportingText = uiState.duplicate?.let { duplicate ->
                {
                    Text(
                        text = buildAnnotatedString {
                            append("Already on ${duplicate.emoji} ${duplicate.islandName}: ")
                            append(inLanguage(duplicate.card.targetText, selectedCoast.language))
                        },
                        color = SunlitText
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        if (mode == InputMode.Manual) {
            CoastTextField(
                value = uiState.targetSentence,
                onValueChange = onTargetSentenceChange,
                label = { Text("${selectedCoast.language} translation") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
        }

        CoastTextField(
            value = uiState.category,
            onValueChange = onCategoryChange,
            // Manual cards fall back to DEFAULT_MANUAL_CATEGORY; the AI picks one otherwise.
            // "Topic" is the plain word; the islands are what the topics are called on the coast.
            label = { Text("Topic (optional)") },
            placeholder = { Text("e.g. Travel") },
            supportingText = {
                Text(
                    text = if (mode == InputMode.Manual) "Cards are grouped by topic, the islands of your coast. Left empty, it goes on $DEFAULT_MANUAL_CATEGORY."
                    else "Cards are grouped by topic, the islands of your coast. Left empty, one is picked for you.",
                    color = TextSecondary
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // A guess from the words already typed, before the AI gets to see them.
        uiState.suggestedIsland?.let { suggested ->
            val emoji = uiState.coastIslands.firstOrNull { it.name == suggested }?.let(::islandEmoji) ?: emojiForCategory(suggested)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(onClickLabel = "use this island", role = Role.Button) { onCategoryChange(suggested) }
                    .semantics(mergeDescendants = true) { contentDescription = "Looks like $suggested" }
                    .padding(vertical = 8.dp)
            ) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = SunlitText, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Looks like $emoji $suggested", style = MaterialTheme.typography.labelMedium, color = SunlitText)
            }
        }

        if (uiState.coastIslands.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(uiState.coastIslands, key = { it.islandId }) { island ->
                    val isSelected = uiState.category.equals(island.name, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onCategoryChange(if (isSelected) "" else island.name) },
                        label = { Text("${islandEmoji(island)} ${island.name}", style = MaterialTheme.typography.labelMedium) },
                        colors = coastChipColors(),
                        border = coastChipBorder(enabled = true, selected = isSelected),
                        shape = MaterialTheme.shapes.small
                    )
                }
            }
        }

        CoastButton(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .heightIn(min = ButtonHeight)
                .testTag("create_save_button"),
            enabled = !uiState.isSaving && uiState.nativeSentence.isNotBlank() && !uiState.isSameLanguage
        ) {
            if (uiState.isSaving) {
                // The button is disabled (dark) while saving, so the spinner needs a light color.
                CircularProgressIndicator(
                    color = TextPrimary,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier
                        .size(22.dp)
                        .semantics { contentDescription = "Saving" }
                )
                // A local model can take a while; the clock shows it is still at it.
                if (mode != InputMode.Manual && uiState.translationProvider == TranslationProvider.LocalAi) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (uiState.elapsedSeconds < 2) "Thinking…" else "Thinking… ${uiState.elapsedSeconds}s",
                        color = TextPrimary,
                        style = MaterialTheme.typography.labelMedium.tabular
                    )
                }
            } else {
                Text(
                    when (mode) {
                        InputMode.Translate -> "Translate and save"
                        InputMode.WriteInTarget -> "Check and save"
                        InputMode.Manual -> "Save card"
                    }
                )
            }
        }

        // Both ways in need a provider that can translate, so they aren't offered while there is none.
        if (mode != InputMode.Manual && uiState.engineReady) {
            // A flow row, so at a large font size the second one moves to its own line instead of squeezing the first.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onBrowseStarters, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = SunlitText, modifier = Modifier.padding(end = 6.dp).size(18.dp))
                    Text("Starter sentences", style = MaterialTheme.typography.labelMedium, color = SunlitText)
                }
                TextButton(onClick = onAddMany, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = TextSecondary, modifier = Modifier.padding(end = 6.dp).size(18.dp))
                    Text(if (photoOffered) "From text or a photo" else "From text", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                }
            }
        }
    }
}

/** Switches the focus coast (which today's session and new cards are on): a compact button that opens a menu of all coasts. */
@Composable
private fun CoastPicker(coasts: List<Coast>, selected: Coast, onSelected: (Coast) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(start = 12.dp, end = 4.dp),
            modifier = Modifier.semantics { contentDescription = "${selected.displayName} is the focus. Change coast" }
        ) {
            Text("Change", color = SunlitText, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = SunlitText)
        }
        // The same raised surface as the app's other menus (see SelectionDropdown).
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = SurfaceSelected) {
            coasts.forEach { coast ->
                val isSelected = coast == selected
                DropdownMenuItem(
                    text = { Text(coast.displayName, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                    trailingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = "Selected", tint = TextPrimary) }
                    } else null,
                    onClick = {
                        onSelected(coast)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * The last few cards added to the selected coast (whose [language] their translations are in), so the user sees
 * their coast growing. Right after saving is when a typo is spotted, so a row opens the card to fix it, and its
 * ⋮ menu edits or deletes it.
 */
@Composable
private fun RecentCards(cards: List<RecentCard>, language: String, onEdit: (Flashcard) -> Unit, onDelete: (Flashcard) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Recently added", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
        ContentCard {
            cards.forEachIndexed { index, recent ->
                if (index > 0) HorizontalDivider(color = SurfaceRaised, modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(onClickLabel = "edit the card", role = Role.Button) { onEdit(recent.card) }
                        .padding(vertical = 6.dp)
                ) {
                    IconTile(size = 40.dp) { TileEmoji(recent.emoji, tileSize = 40.dp) }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            inLanguage(recent.card.targetText, language),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            recent.card.nativeText,
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // Capped, so a long island name is cut and not the card's own text.
                    Pill(text = recent.islandName, modifier = Modifier.widthIn(max = 100.dp), container = SurfaceRaised, content = TextSecondary)
                    CardOptionsMenu(
                        onEdit = { onEdit(recent.card) },
                        onDelete = { onDelete(recent.card) },
                        description = "Options for ${recent.card.nativeText}"
                    )
                }
            }
        }
    }
}

/**
 * The two steps of onboarding, shown until the user has a coast: the language they speak, then the one
 * they want to learn, with what a coast and an island are. Starting the coast then asks why they learn it
 * (see [GoalSheet]).
 */
@Composable
private fun Onboarding(
    nativeLanguage: String,
    availableLanguages: List<Language>,
    onNativeLanguage: (String) -> Unit,
    onStartCoast: () -> Unit
) {
    var step by rememberSaveable { mutableStateOf(1) }
    var showNativePicker by rememberSaveable { mutableStateOf(false) }
    val native = Languages.byName(nativeLanguage)

    PlatformBackHandler(enabled = step == 2) { step = 1 }

    // Most people speak the language their phone is set to, so that is picked to start with, unless a language
    // was chosen already. The welcome only shows once the settings are read, so the stored choice is known here.
    LaunchedEffect(Unit) {
        if (nativeLanguage == SettingsDefaults.NATIVE_LANGUAGE) {
            phoneLanguage()?.takeIf { it.name != nativeLanguage }?.let { onNativeLanguage(it.name) }
        }
    }

    if (showNativePicker) {
        LanguagePickerDialog(
            title = "Native language",
            subtitle = "The language you think in.",
            languages = Languages.ALL,
            selected = nativeLanguage,
            onPick = { language ->
                onNativeLanguage(language.name)
                showNativePicker = false
            },
            onDismiss = { showNativePicker = false }
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenMargin)
    ) {
        // What the app is for comes first: the logo is already in the header above.
        Text(
            "Learn the sentences you'll actually say",
            style = MaterialTheme.typography.headlineSmall,
            color = SunlitText,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "A few minutes a day, picked for why you're learning.",
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))
        // The goal question that follows "Start a coast" is the third step.
        Text("Step $step of 3", style = MaterialTheme.typography.labelMedium, color = SunlitText)
        Spacer(modifier = Modifier.height(6.dp))
        if (step == 1) {
            Text(
                "Which language do you speak?",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Cards are explained and translated into it.",
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            ContentCard(onClick = { showNativePicker = true }, onClickLabel = "change your language") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile { TileEmoji(native?.flag ?: "🌍") }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(nativeLanguage, style = MaterialTheme.typography.titleMedium)
                        native?.nativeName?.takeIf { it != nativeLanguage }?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                    }
                    Text("Change", style = MaterialTheme.typography.labelMedium, color = SunlitText)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            CoastButton(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight)) {
                Text("Continue")
            }
        } else {
            Text(
                "Which language do you want to learn?",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Each language is a coast. Its cards are grouped on islands, one for each topic. " +
                    "Next you'll be asked why you're learning it, so that new sentences fit.",
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            CoastButton(
                onClick = onStartCoast,
                enabled = availableLanguages.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Start a coast")
            }
            Spacer(modifier = Modifier.height(10.dp))
            CoastSecondaryButton(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight)) {
                Text("Back")
            }
        }
    }
}

/** The language the phone is set to, if a coast can be in it ("nb" and "nn" both being Norwegian). */
private fun phoneLanguage(): Language? {
    val code = Locale.current.language.let { if (it == "nb" || it == "nn") "no" else it }
    return Languages.ALL.firstOrNull { it.code == code }
}

/**
 * A calm checklist under today's session for someone new: translation working, a first session and a first
 * backup. Each step ticks itself off from what really happened, and the card goes once all three are done or
 * the user hides it. No popups: it waits here until wanted.
 */
@Composable
private fun GettingStartedCard(
    steps: GettingStarted,
    onSetUpTranslation: () -> Unit,
    onBackUp: () -> Unit,
    onHowItWorks: () -> Unit,
    onHide: () -> Unit
) {
    ContentCard(padding = PaddingValues(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
            Text("Getting started", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
            Text("${steps.doneCount} of 3", style = MaterialTheme.typography.labelMedium.tabular, color = TextSecondary)
        }
        Spacer(modifier = Modifier.height(4.dp))
        ChecklistStep(done = steps.translationReady, text = if (steps.translationReady) "Translation works" else "Set up translation", onClick = onSetUpTranslation)
        ChecklistStep(done = steps.studied, text = "Finish your first session", onClick = null)
        ChecklistStep(done = steps.backedUp, text = "Back up your cards once", detail = "They're saved on this phone only.", onClick = onBackUp)
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onHowItWorks) { Text("New here? See how it works", style = MaterialTheme.typography.labelMedium, color = SunlitText) }
            TextButton(onClick = onHide) { Text("Hide", style = MaterialTheme.typography.labelMedium, color = TextSecondary) }
        }
    }
}

/** One step of [GettingStartedCard]: ticked when done, otherwise tappable where there is somewhere to go. */
@Composable
private fun ChecklistStep(done: Boolean, text: String, onClick: (() -> Unit)?, detail: String? = null) {
    val tappable = onClick != null && !done
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (tappable) Modifier.clickable(role = Role.Button, onClick = onClick!!) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = if (done) "$text, done" else text }
            .padding(vertical = 6.dp)
    ) {
        Icon(
            imageVector = if (done) Icons.Default.Check else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (done) Seafoam else TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (done) TextSecondary else if (tappable) SunlitText else TextPrimary
            )
            if (detail != null && !done) Text(detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

/**
 * Shows the card that was just saved, so a bad translation can be spotted and undone or fixed right
 * away: the pencil turns the translation into a field. A sentence written in the coast's language
 * shows what was corrected. [language] is the coast's, which the translation is in.
 */
@Composable
private fun SavedCard(
    result: SaveResult.Saved,
    language: String,
    onUndo: () -> Unit,
    onEditTranslation: (String) -> Unit,
    onStartEditing: () -> Unit,
    onCancelEditing: () -> Unit
) {
    var editing by rememberSaveable(result.added.card.cardId) { mutableStateOf(false) }
    var draft by rememberSaveable(result.added.card.cardId) { mutableStateOf(result.targetText) }
    ContentCard(
        // Read out when it appears: the button the user was on has become a spinner and says nothing of the result.
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        padding = PaddingValues(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Seafoam, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Saved to ${result.emoji} ${result.category}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            if (!editing) {
                IconButton(onClick = {
                    editing = true
                    onStartEditing()
                }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit translation", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
                // A gap between the two targets, so reaching for Undo doesn't open the editor or the other way round.
                Spacer(modifier = Modifier.width(8.dp))
            }
            TextButton(onClick = onUndo) {
                Text("Undo", color = SunlitText, style = MaterialTheme.typography.labelMedium)
            }
        }
        if (editing) {
            CoastTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Translation") },
                // A sentence, often longer than the field is wide: it wraps so the whole of it can be checked while editing.
                minLines = 2,
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp, top = 4.dp)
            )
            Row(modifier = Modifier.fillMaxWidth().padding(end = 12.dp, top = 4.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    draft = result.targetText
                    editing = false
                    onCancelEditing()
                }) { Text("Cancel", color = TextSecondary, style = MaterialTheme.typography.labelMedium) }
                TextButton(onClick = {
                    onEditTranslation(draft)
                    editing = false
                }, enabled = draft.isNotBlank(), colors = ButtonDefaults.textButtonColors(contentColor = SunlitText)) {
                    Text("Save", style = MaterialTheme.typography.labelMedium)
                }
            }
        } else {
            Text(
                text = inLanguage(result.targetText, language),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(end = 12.dp, top = 2.dp)
            )
        }
        Text(
            text = result.nativeText,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(end = 12.dp)
        )
        if (result.onDevice) {
            // The on-device translator's terms ask for its name next to what it translated.
            Text(
                text = "Translated by Google Translate, on your phone",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(end = 12.dp, top = 6.dp)
            )
        }
        result.corrected?.let { written ->
            HorizontalDivider(color = SurfaceRaised, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp, end = 12.dp))
            Text("You wrote: $written", style = MaterialTheme.typography.bodyMedium, color = CoralText, modifier = Modifier.padding(end = 12.dp))
            result.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(end = 12.dp, top = 2.dp)) }
        }
    }
}

/** Shows why a card couldn't be saved, with a Retry button (or a cloud fallback when the local model keeps failing). */
@Composable
private fun ErrorCard(message: String, fallback: TranslationProvider?, onRetry: () -> Unit) {
    // Read out at once when it appears, like the result of a save.
    ContentCard(modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Error, contentDescription = null, tint = CoralText)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(10.dp))
        CoastSecondaryButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight)) {
            Text(if (fallback != null) "Try with ${fallback.label}" else "Try again")
        }
    }
}

/**
 * A sheet that turns a paragraph (pasted, or read from a photo) into sentences to tick, then saves a
 * card for each ticked one with the provider picked in Settings.
 *
 * @param writing The language the paragraph is written in.
 * @param text The paragraph so far. It is kept by the caller, which is also where a photo's text arrives.
 * @param readPhoto Opens the photo picker; null where photos can't be read, or not in the [writing] language.
 * @param readingPhoto Whether a picked photo is being read right now.
 * @param photoError Why the last photo gave no text. Shown here in the sheet: a snackbar would sit behind it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatchSheet(
    batch: BatchState?,
    writing: String,
    text: String,
    onTextChange: (String) -> Unit,
    readPhoto: (() -> Unit)?,
    readingPhoto: Boolean,
    photoError: String?,
    onStartBatch: (String) -> Unit,
    onToggle: (Int) -> Unit,
    onSave: () -> Unit,
    onEditText: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmStopBatch by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberGuardedSheetState(guarded = batch?.isSaving == true, onBlocked = { confirmStopBatch = true })
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            Text("Add many cards", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            when {
                batch == null -> {
                    Text(
                        text = if (readPhoto != null) "Paste a paragraph in $writing, or read one from a photo of a page or a sign. Each sentence becomes a card."
                        else "Paste a paragraph in $writing. Each sentence becomes a card.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    CoastTextField(
                        value = text,
                        onValueChange = onTextChange,
                        label = { Text("Paste text here") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4
                    )
                    if (readPhoto != null) {
                        CoastSecondaryButton(onClick = readPhoto, enabled = !readingPhoto, modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight)) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text("Read from a photo")
                        }
                        if (readingPhoto) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = SunlitText, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Reading the photo…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                                )
                            }
                        } else if (photoError != null) {
                            Text(
                                text = photoError,
                                style = MaterialTheme.typography.bodyMedium,
                                color = CoralText,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            )
                        }
                    }
                    CoastButton(
                        onClick = { onStartBatch(text) },
                        enabled = text.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight)
                    ) {
                        Text("Split into sentences")
                    }
                }
                batch.sentences.isEmpty() -> {
                    Text("No sentences were found in that text.", color = TextSecondary)
                    // Back to the field with the text still in it: closing would throw away what was pasted or read.
                    CoastButton(onClick = onEditText, modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight)) { Text("Edit the text") }
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Close", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                    }
                }
                else -> {
                    val picked = batch.sentences.count { it.checked }
                    if (batch.saved == null) {
                        Text("Untick anything you don't want.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                    ContentCard(padding = PaddingValues(vertical = 4.dp, horizontal = 4.dp)) {
                        batch.sentences.forEachIndexed { index, sentence ->
                            // The row is the tick box: one stop for a screen reader, which hears the sentence and whether it is ticked.
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .toggleable(value = sentence.checked, enabled = batch.saved == null, role = Role.Checkbox) { onToggle(index) }
                                    .padding(end = 12.dp)
                            ) {
                                Checkbox(
                                    checked = sentence.checked,
                                    onCheckedChange = null,
                                    // Without a handler of its own the box is small; the padding keeps the row as tall as before.
                                    modifier = Modifier.padding(12.dp),
                                    enabled = batch.saved == null,
                                    colors = CheckboxDefaults.colors(checkedColor = Sunlit, checkmarkColor = OnSunlit, uncheckedColor = TextSecondary)
                                )
                                Text(sentence.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    when {
                        batch.isDone -> {
                            val saved = (batch.saved ?: 0) - batch.failed
                            Text(
                                text = if (batch.failed == 0) "${plural(saved, "card")} added." else "${plural(saved, "card")} added, ${batch.failed} couldn't be translated.",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            )
                            // The reason, so the user knows whether it is the key, the connection or the language.
                            val reason = batch.lastError
                            if (batch.failed > 0 && reason != null) {
                                Text(reason, style = MaterialTheme.typography.bodyMedium, color = CoralText)
                            }
                            CoastButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight)) { Text("Done") }
                        }
                        batch.isSaving -> {
                            LinearProgressIndicator(
                                progress = { (batch.saved ?: 0).toFloat() / picked },
                                color = Sunlit,
                                trackColor = SurfaceRaised,
                                strokeCap = StrokeCap.Round,
                                gapSize = 0.dp,
                                drawStopIndicator = {},
                                modifier = Modifier.fillMaxWidth().height(6.dp)
                            )
                            Text("Translating ${(batch.saved ?: 0) + 1} of $picked…", style = MaterialTheme.typography.bodyMedium.tabular, color = TextSecondary)
                        }
                        else -> CoastButton(
                            onClick = onSave,
                            enabled = picked > 0,
                            modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight)
                        ) {
                            Text("Add ${plural(picked, "card")}")
                        }
                    }
                }
            }
        }
    }
    if (confirmStopBatch) {
        AlertDialog(
            onDismissRequest = { confirmStopBatch = false },
            title = { Text("Stop adding cards?") },
            text = { Text("Cards already translated stay on your coast; remaining sentences won't be added.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmStopBatch = false
                    onDismiss()
                }) { Text("Stop", color = CoralText, style = MaterialTheme.typography.labelMedium) }
            },
            dismissButton = {
                TextButton(onClick = { confirmStopBatch = false }) {
                    Text("Keep going", color = TextPrimary, style = MaterialTheme.typography.labelMedium)
                }
            }
        )
    }
}
