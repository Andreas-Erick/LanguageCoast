package com.andreaserick.languagecoast.ui.create

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlaylistAdd
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andreaserick.languagecoast.data.Coast
import com.andreaserick.languagecoast.data.Language
import com.andreaserick.languagecoast.data.Languages
import com.andreaserick.languagecoast.data.RecentCard
import com.andreaserick.languagecoast.data.TranslationProvider
import com.andreaserick.languagecoast.data.emojiForCategory
import com.andreaserick.languagecoast.data.islandEmoji
import com.andreaserick.languagecoast.resources.Res
import com.andreaserick.languagecoast.resources.logo_mark
import com.andreaserick.languagecoast.ui.components.ButtonHeight
import com.andreaserick.languagecoast.ui.components.CoastButton
import com.andreaserick.languagecoast.ui.components.CoastSecondaryButton
import com.andreaserick.languagecoast.ui.components.CoastTextField
import com.andreaserick.languagecoast.ui.components.ContentCard
import com.andreaserick.languagecoast.ui.components.FeedbackKind
import com.andreaserick.languagecoast.ui.components.IconTile
import com.andreaserick.languagecoast.ui.components.LanguagePickerDialog
import com.andreaserick.languagecoast.ui.components.LocalFeedback
import com.andreaserick.languagecoast.ui.components.LocalUndoMessenger
import com.andreaserick.languagecoast.ui.components.Pill
import com.andreaserick.languagecoast.ui.components.ScreenHeader
import com.andreaserick.languagecoast.ui.components.ScreenMargin
import com.andreaserick.languagecoast.ui.components.SegmentedSwitch
import com.andreaserick.languagecoast.ui.components.coastChipBorder
import com.andreaserick.languagecoast.ui.components.coastChipColors
import com.andreaserick.languagecoast.ui.components.plural
import com.andreaserick.languagecoast.ui.theme.CoralText
import com.andreaserick.languagecoast.ui.theme.OnSunlit
import com.andreaserick.languagecoast.ui.theme.Seafoam
import com.andreaserick.languagecoast.ui.theme.SurfaceRaised
import com.andreaserick.languagecoast.ui.theme.Sunlit
import com.andreaserick.languagecoast.ui.theme.TextPrimary
import com.andreaserick.languagecoast.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The "Create" screen. Lets users create new flashcards from a sentence: translated by the provider
 * picked in Settings, written in the coast's language and checked by the AI, or typed by hand.
 * Whole paragraphs and photos can be captured as a batch of cards.
 */
@Composable
fun CreateScreen(
    onStartReview: () -> Unit,
    viewModel: CreateViewModel = koinViewModel()
) {
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
        onStartBatch = viewModel::startBatch,
        onToggleBatchSentence = viewModel::toggleBatchSentence,
        onSaveBatch = viewModel::saveBatch,
        onDismissBatch = viewModel::dismissBatch,
        onStartReview = onStartReview
    )
}

/**
 * The home screen's content: onboarding until there is a coast, otherwise today's reviews,
 * the new card form, the result of the last save and the most recently added cards.
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
    onStartBatch: (String) -> Unit,
    onToggleBatchSentence: (Int) -> Unit,
    onSaveBatch: () -> Unit,
    onDismissBatch: () -> Unit,
    onStartReview: () -> Unit
) {
    var showNewCoastDialog by remember { mutableStateOf(false) }
    var showBatchSheet by remember { mutableStateOf(false) }

    if (showNewCoastDialog) {
        LanguagePickerDialog(
            title = "New Coast",
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
            coastLanguage = uiState.selectedCoast?.language.orEmpty(),
            nativeLanguage = uiState.nativeLanguage,
            inputMode = uiState.inputMode,
            onStartBatch = onStartBatch,
            onToggle = onToggleBatchSentence,
            onSave = onSaveBatch,
            onDismiss = {
                showBatchSheet = false
                onDismissBatch()
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Language Coast",
            leading = {
                // The mark sits in the middle of its canvas, so it's drawn larger than it looks and pulled left.
                Image(
                    painter = painterResource(Res.drawable.logo_mark),
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .offset(x = (-8).dp)
                )
            },
            actions = { if (uiState.streakCount > 0) StreakBadge(uiState.streakCount) }
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
                if (uiState.hasCards) {
                    TodayCard(dueCount = uiState.dueCount, dueCoastCount = uiState.dueCoastCount, onStartReview = onStartReview)
                } else {
                    FirstSentenceHint(coastLanguage = selectedCoast.language)
                }

                NewCardForm(
                    uiState = uiState,
                    selectedCoast = selectedCoast,
                    onNativeSentenceChange = onNativeSentenceChange,
                    onTargetSentenceChange = onTargetSentenceChange,
                    onCategoryChange = onCategoryChange,
                    onInputModeChange = onInputModeChange,
                    onCoastSelected = onCoastSelected,
                    onSave = onSave,
                    onAddMany = { showBatchSheet = true }
                )

                // The preview appears below the form, often off screen; scroll to it once it has expanded.
                val resultRequester = remember { BringIntoViewRequester() }
                val feedback = LocalFeedback.current
                LaunchedEffect(uiState.result) {
                    when (uiState.result) {
                        is SaveResult.Saved -> feedback.play(FeedbackKind.Success)
                        is SaveResult.Error -> feedback.play(FeedbackKind.Failure)
                        null -> return@LaunchedEffect
                    }
                    delay(RESULT_SCROLL_DELAY_MILLIS)
                    resultRequester.bringIntoView()
                }
                AnimatedVisibility(
                    visible = uiState.result != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                    modifier = Modifier.bringIntoViewRequester(resultRequester)
                ) {
                    when (val result = uiState.result) {
                        is SaveResult.Saved -> SavedCard(result, onUndo = onUndo, onEditTranslation = onEditSavedTranslation)
                        is SaveResult.Error -> ErrorCard(result.message, fallback = uiState.fallbackProvider, onRetry = onRetryWithFallback)
                        null -> Unit
                    }
                }

                if (uiState.recentCards.isNotEmpty()) RecentCards(uiState.recentCards)
            }
        }
    }
}

/** Long enough for the save preview's expand animation to finish before scrolling to it. */
private const val RESULT_SCROLL_DELAY_MILLIS = 350L

/** The streak shown in the header, e.g. "4 days". */
@Composable
private fun StreakBadge(streak: Int) {
    Text(
        text = plural(streak, "day"),
        style = MaterialTheme.typography.labelMedium,
        color = Sunlit,
        maxLines = 1,
        modifier = Modifier
            .padding(start = 8.dp, end = 12.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${plural(streak, "day")} streak" }
    )
}

/** How many cards are due today, with a button that starts reviewing them. */
@Composable
private fun TodayCard(dueCount: Int, dueCoastCount: Int, onStartReview: () -> Unit) {
    ContentCard(padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (dueCount == 0) "All caught up" else "${plural(dueCount, "card")} due today",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = when {
                        dueCount == 0 -> "Nothing to review right now."
                        dueCoastCount > 1 -> "Across $dueCoastCount coasts, taking turns"
                        else -> "Ready when you are"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            if (dueCount > 0) {
                Spacer(modifier = Modifier.width(12.dp))
                CoastButton(
                    onClick = onStartReview,
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text("Review", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

/** The last step of onboarding, shown until the first card exists. */
@Composable
private fun FirstSentenceHint(coastLanguage: String) {
    ContentCard(padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(size = 40.dp) { Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Sunlit) }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("Step 3 of 3 · Your first sentence", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Type anything you'd like to say in $coastLanguage. It is translated and filed on an island for you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
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
    onCoastSelected: (Coast) -> Unit,
    onSave: () -> Unit,
    onAddMany: () -> Unit
) {
    val mode = uiState.inputMode
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text("New card", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            CoastPicker(coasts = uiState.coasts, selected = selectedCoast, onSelected = onCoastSelected)
        }

        if (uiState.isSameLanguage) {
            Text(
                text = sameLanguageMessage(selectedCoast.language),
                color = CoralText,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        SegmentedSwitch(
            options = listOf("AI translates", "I write it", "Manual"),
            selectedIndex = mode.ordinal,
            onSelect = { onInputModeChange(InputMode.entries[it]) },
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = when (mode) {
                InputMode.Translate -> "${uiState.translationProvider.label} · ${uiState.nativeLanguage} → ${selectedCoast.language}"
                InputMode.WriteInTarget -> "Write in ${selectedCoast.language}. ${uiState.translationProvider.label} fills in the ${uiState.nativeLanguage} and corrects slips."
                InputMode.Manual -> "You type both sides."
            },
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )

        CoastTextField(
            value = uiState.nativeSentence,
            onValueChange = onNativeSentenceChange,
            label = { Text(if (mode == InputMode.WriteInTarget) "Write it in ${selectedCoast.language}" else "What do you want to say?") },
            placeholder = { Text(if (mode == InputMode.WriteInTarget) "e.g., Wo ist der Bahnhof?" else "e.g., Where is the train station?") },
            // Only a hint: the user may want a second card, e.g. on another island.
            supportingText = uiState.duplicate?.let { duplicate ->
                {
                    Text(
                        "Already on ${duplicate.emoji} ${duplicate.islandName}: ${duplicate.card.targetText}",
                        color = Sunlit
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
                label = { Text("${selectedCoast.language} Translation") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
        }

        CoastTextField(
            value = uiState.category,
            onValueChange = onCategoryChange,
            // Manual cards fall back to DEFAULT_MANUAL_CATEGORY; the AI picks one otherwise.
            label = { Text("Island (optional)") },
            placeholder = { Text("e.g., Travel") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // A guess from the words already typed, before the AI gets to see them.
        uiState.suggestedIsland?.let { suggested ->
            val emoji = uiState.coastIslands.firstOrNull { it.name == suggested }?.let(::islandEmoji) ?: emojiForCategory(suggested)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { onCategoryChange(suggested) }
                    .semantics(mergeDescendants = true) { contentDescription = "Looks like $suggested. Tap to use it." }
                    .padding(vertical = 2.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Sunlit, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Looks like $emoji $suggested", style = MaterialTheme.typography.labelMedium, color = Sunlit)
            }
        }

        if (uiState.coastIslands.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                uiState.coastIslands.forEach { island ->
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
                .heightIn(min = ButtonHeight),
            enabled = !uiState.isSaving && uiState.nativeSentence.isNotBlank() && !uiState.isSameLanguage
        ) {
            if (uiState.isSaving) {
                // The button is disabled (dark) while saving, so the spinner needs a light color.
                CircularProgressIndicator(
                    color = TextPrimary,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(22.dp)
                )
                // A local model can take a while; the clock shows it is still at it.
                if (mode != InputMode.Manual && uiState.translationProvider == TranslationProvider.LocalAi) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (uiState.elapsedSeconds < 2) "Thinking…" else "Thinking… ${uiState.elapsedSeconds}s",
                        color = TextPrimary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            } else {
                Text(
                    when (mode) {
                        InputMode.Translate -> "Translate & Save"
                        InputMode.WriteInTarget -> "Check & Save"
                        InputMode.Manual -> "Save Flashcard"
                    }
                )
            }
        }

        if (mode != InputMode.Manual) {
            TextButton(
                onClick = onAddMany,
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = TextSecondary, modifier = Modifier.padding(end = 6.dp).size(18.dp))
                Text("Add many from text or a photo", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }
        }
    }
}

/** The coast new cards are added to, as a compact button that opens a menu of all coasts. */
@Composable
private fun CoastPicker(coasts: List<Coast>, selected: Coast, onSelected: (Coast) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(start = 12.dp, end = 4.dp),
            modifier = Modifier.semantics { contentDescription = "Adding to ${selected.displayName}. Change coast" }
        ) {
            Text(
                selected.displayName,
                color = TextPrimary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextPrimary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            coasts.forEach { coast ->
                val isSelected = coast == selected
                DropdownMenuItem(
                    text = { Text(coast.displayName, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                    trailingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = "Selected") }
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

/** The last few cards added to the selected coast, so the user sees their coast growing. */
@Composable
private fun RecentCards(cards: List<RecentCard>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Recently added", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        ContentCard {
            cards.forEachIndexed { index, recent ->
                if (index > 0) HorizontalDivider(color = SurfaceRaised, modifier = Modifier.padding(vertical = 10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(size = 40.dp) { Text(recent.emoji, fontSize = 18.sp) }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            recent.card.targetText,
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
                    Pill(text = recent.islandName, container = SurfaceRaised, content = TextSecondary)
                }
            }
        }
    }
}

/**
 * The first two steps of onboarding, shown until the user has a coast: the language they speak,
 * then the one they want to learn. The third step is their first sentence, hinted on the form.
 */
@Composable
private fun Onboarding(
    nativeLanguage: String,
    availableLanguages: List<Language>,
    onNativeLanguage: (String) -> Unit,
    onStartCoast: () -> Unit
) {
    var step by remember { mutableStateOf(1) }
    var showNativePicker by remember { mutableStateOf(false) }
    val native = Languages.byName(nativeLanguage)

    if (showNativePicker) {
        LanguagePickerDialog(
            title = "Your language",
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
        Image(
            painter = painterResource(Res.drawable.logo_mark),
            contentDescription = "Language Coast",
            modifier = Modifier.size(150.dp)
        )
        Text("Step $step of 3", style = MaterialTheme.typography.labelMedium, color = Sunlit)
        Spacer(modifier = Modifier.height(6.dp))
        if (step == 1) {
            Text("Which language do you speak?", style = MaterialTheme.typography.headlineMedium, color = TextPrimary, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Cards are explained and translated into it.",
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            ContentCard(onClick = { showNativePicker = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile { Text(native?.flag ?: "🌍", fontSize = 24.sp) }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(nativeLanguage, style = MaterialTheme.typography.titleMedium)
                        native?.nativeName?.takeIf { it != nativeLanguage }?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                    }
                    Text("Change", style = MaterialTheme.typography.labelMedium, color = Sunlit)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            CoastButton(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight)) {
                Text("Continue")
            }
        } else {
            Text("Which language do you want to learn?", style = MaterialTheme.typography.headlineMedium, color = TextPrimary, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Each language is a coast. You can start more coasts later.",
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
                Text("Start a Coast")
            }
            Spacer(modifier = Modifier.height(10.dp))
            CoastSecondaryButton(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight)) {
                Text("Back")
            }
        }
    }
}

/**
 * Shows the card that was just saved, so a bad translation can be spotted and undone or fixed right
 * away: the pencil turns the translation into a field. A sentence written in the coast's language
 * shows what was corrected.
 */
@Composable
private fun SavedCard(result: SaveResult.Saved, onUndo: () -> Unit, onEditTranslation: (String) -> Unit) {
    var editing by remember(result.added.card.cardId) { mutableStateOf(false) }
    var draft by remember(result.added.card.cardId) { mutableStateOf(result.targetText) }
    ContentCard(padding = PaddingValues(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Seafoam, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Saved to ${result.emoji} ${result.category}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            if (!editing) {
                IconButton(onClick = { editing = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit translation", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
            TextButton(onClick = onUndo) {
                Text("Undo", color = Sunlit, style = MaterialTheme.typography.labelMedium)
            }
        }
        if (editing) {
            CoastTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Translation") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp, top = 4.dp)
            )
            Row(modifier = Modifier.fillMaxWidth().padding(end = 12.dp, top = 4.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    draft = result.targetText
                    editing = false
                }) { Text("Cancel", color = TextSecondary, style = MaterialTheme.typography.labelMedium) }
                TextButton(onClick = {
                    onEditTranslation(draft)
                    editing = false
                }, enabled = draft.isNotBlank()) { Text("Save", color = Sunlit, style = MaterialTheme.typography.labelMedium) }
            }
        } else {
            Text(
                text = result.targetText,
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
        result.corrected?.let { written ->
            HorizontalDivider(color = SurfaceRaised, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp, end = 12.dp))
            Text("You wrote: $written", style = MaterialTheme.typography.bodyMedium, color = CoralText, modifier = Modifier.padding(end = 12.dp))
            result.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(end = 12.dp, top = 2.dp)) }
        }
    }
}

/** Shows why a card couldn't be saved, and offers a cloud provider when the local model keeps failing. */
@Composable
private fun ErrorCard(message: String, fallback: TranslationProvider?, onRetry: () -> Unit) {
    ContentCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Error, contentDescription = null, tint = CoralText)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
        if (fallback != null) {
            Spacer(modifier = Modifier.height(10.dp))
            CoastSecondaryButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                Text("Try with ${fallback.label}", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * A sheet that turns a paragraph (pasted, or read from a photo) into sentences to tick, then saves a
 * card for each ticked one with the provider picked in Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatchSheet(
    batch: BatchState?,
    coastLanguage: String,
    nativeLanguage: String,
    inputMode: InputMode,
    onStartBatch: (String) -> Unit,
    onToggle: (Int) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    val undoMessenger = LocalUndoMessenger.current
    val readPhoto = rememberPhotoTextReader(onText = { text = it }, onError = { undoMessenger.showMessage(it) })
    val writing = if (inputMode == InputMode.WriteInTarget) coastLanguage else nativeLanguage
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            Text("Add many cards", style = MaterialTheme.typography.titleLarge)
            when {
                batch == null -> {
                    Text(
                        "Paste a paragraph in $writing, or read one from a photo of a page or a sign. Each sentence becomes a card.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    CoastTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Paste text here") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4
                    )
                    if (readPhoto != null) {
                        CoastSecondaryButton(onClick = readPhoto, modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight)) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text("Read from a photo")
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
                    CoastSecondaryButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight)) { Text("Close") }
                }
                else -> {
                    val picked = batch.sentences.count { it.checked }
                    if (batch.saved == null) {
                        Text("Untick anything you don't want.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                    ContentCard(padding = PaddingValues(vertical = 4.dp, horizontal = 4.dp)) {
                        batch.sentences.forEachIndexed { index, sentence ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = batch.saved == null) { onToggle(index) }
                                    .padding(end = 12.dp)
                            ) {
                                Checkbox(
                                    checked = sentence.checked,
                                    onCheckedChange = { onToggle(index) },
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
                                style = MaterialTheme.typography.titleMedium
                            )
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
                            Text("Translating ${(batch.saved ?: 0) + 1} of $picked…", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
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
}
