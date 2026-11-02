package com.andreaserick.languagecoast.ui.create

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andreaserick.languagecoast.data.Coast
import com.andreaserick.languagecoast.data.RecentCard
import com.andreaserick.languagecoast.data.islandEmoji
import com.andreaserick.languagecoast.resources.Res
import com.andreaserick.languagecoast.resources.logo_mark
import com.andreaserick.languagecoast.ui.components.ButtonHeight
import com.andreaserick.languagecoast.ui.components.CoastButton
import com.andreaserick.languagecoast.ui.components.CoastTextField
import com.andreaserick.languagecoast.ui.components.ContentCard
import com.andreaserick.languagecoast.ui.components.IconTile
import com.andreaserick.languagecoast.ui.components.LanguagePickerDialog
import com.andreaserick.languagecoast.ui.components.Pill
import com.andreaserick.languagecoast.ui.components.ScreenHeader
import com.andreaserick.languagecoast.ui.components.ScreenMargin
import com.andreaserick.languagecoast.ui.components.SegmentedSwitch
import com.andreaserick.languagecoast.ui.components.coastChipBorder
import com.andreaserick.languagecoast.ui.components.coastChipColors
import com.andreaserick.languagecoast.ui.components.plural
import com.andreaserick.languagecoast.ui.theme.CoralText
import com.andreaserick.languagecoast.ui.theme.Seafoam
import com.andreaserick.languagecoast.ui.theme.SurfaceRaised
import com.andreaserick.languagecoast.ui.theme.Sunlit
import com.andreaserick.languagecoast.ui.theme.TextPrimary
import com.andreaserick.languagecoast.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The "Create" screen. Lets users create new flashcards from a native sentence, either
 * manually (providing their own translation and category) or with the translation provider picked
 * in Settings (On-device, Local AI, Gemini, OpenAI, Claude, Groq, DeepSeek or OpenRouter).
 */
@Composable
fun CreateScreen(
    onStartReview: (islandId: Int, islandName: String) -> Unit,
    viewModel: CreateViewModel = koinViewModel()
) {
    CreateContent(
        uiState = viewModel.uiState,
        onNativeSentenceChange = viewModel::onNativeSentenceChange,
        onTargetSentenceChange = viewModel::onTargetSentenceChange,
        onCategoryChange = viewModel::onCategoryChange,
        onManualModeChange = viewModel::onManualModeChange,
        onCoastSelected = viewModel::onCoastSelected,
        onAddCoast = viewModel::addCoast,
        onSave = viewModel::save,
        onUndo = viewModel::undoLastSave,
        onStartReview = { viewModel.startReview(onStartReview) }
    )
}

/**
 * The home screen's content: a prompt to start a coast if there is none, otherwise today's reviews,
 * the new card form, the result of the last save and the most recently added cards.
 */
@Composable
private fun CreateContent(
    uiState: CreateUiState,
    onNativeSentenceChange: (String) -> Unit,
    onTargetSentenceChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onManualModeChange: (Boolean) -> Unit,
    onCoastSelected: (Coast) -> Unit,
    onAddCoast: (String) -> Unit,
    onSave: () -> Unit,
    onUndo: () -> Unit,
    onStartReview: () -> Unit
) {
    var showNewCoastDialog by remember { mutableStateOf(false) }

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
            selectedCoast == null -> StartCoastPrompt(onStartCoast = { showNewCoastDialog = true })
            else -> Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ScreenMargin, vertical = 12.dp)
            ) {
                if (uiState.hasCards) {
                    TodayCard(dueCount = uiState.dueCount, dueCoastCount = uiState.dueCoastCount, onStartReview = onStartReview)
                }

                NewCardForm(
                    uiState = uiState,
                    selectedCoast = selectedCoast,
                    onNativeSentenceChange = onNativeSentenceChange,
                    onTargetSentenceChange = onTargetSentenceChange,
                    onCategoryChange = onCategoryChange,
                    onManualModeChange = onManualModeChange,
                    onCoastSelected = onCoastSelected,
                    onSave = onSave
                )

                // The preview appears below the form, often off screen; scroll to it once it has expanded.
                val resultRequester = remember { BringIntoViewRequester() }
                val haptics = LocalHapticFeedback.current
                LaunchedEffect(uiState.result) {
                    when (uiState.result) {
                        is SaveResult.Saved -> haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        is SaveResult.Error -> haptics.performHapticFeedback(HapticFeedbackType.Reject)
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
                        is SaveResult.Saved -> SavedCard(result, onUndo = onUndo)
                        is SaveResult.Error -> ErrorCard(result.message)
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
                        dueCoastCount > 1 -> "Across $dueCoastCount coasts"
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
    onManualModeChange: (Boolean) -> Unit,
    onCoastSelected: (Coast) -> Unit,
    onSave: () -> Unit
) {
    val isManualMode = uiState.isManualMode
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
            options = listOf("AI translates", "I'll type it"),
            selectedIndex = if (isManualMode) 1 else 0,
            onSelect = { onManualModeChange(it == 1) },
            modifier = Modifier.fillMaxWidth()
        )

        if (!isManualMode) {
            Text(
                text = "${uiState.translationProvider.label} · ${uiState.nativeLanguage} → ${selectedCoast.language}",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        CoastTextField(
            value = uiState.nativeSentence,
            onValueChange = onNativeSentenceChange,
            label = { Text("What do you want to say?") },
            placeholder = { Text("e.g., Where is the train station?") },
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

        if (isManualMode) {
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
                .height(ButtonHeight),
            enabled = !uiState.isSaving && uiState.nativeSentence.isNotBlank() && !uiState.isSameLanguage
        ) {
            if (uiState.isSaving) {
                // The button is disabled (dark) while saving, so the spinner needs a light color.
                CircularProgressIndicator(
                    color = TextPrimary,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Text(if (isManualMode) "Save Flashcard" else "Translate & Save")
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

/** Shown instead of the home screen until the user has started their first coast. */
@Composable
private fun StartCoastPrompt(onStartCoast: () -> Unit) {
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
            modifier = Modifier.size(180.dp)
        )
        Text("Welcome!", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Start a coast for the language you want to learn. You can add more coasts later.",
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        CoastButton(
            onClick = onStartCoast,
            modifier = Modifier
                .fillMaxWidth()
                .height(ButtonHeight)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text("Start a Coast")
        }
    }
}

/** Shows the card that was just saved, so a bad translation can be spotted and undone right away. */
@Composable
private fun SavedCard(result: SaveResult.Saved, onUndo: () -> Unit) {
    ContentCard(padding = PaddingValues(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Seafoam, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Saved to ${result.emoji} ${result.category}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onUndo) {
                Text("Undo", color = Sunlit, style = MaterialTheme.typography.labelMedium)
            }
        }
        Text(
            text = result.targetText,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(end = 12.dp, top = 2.dp)
        )
        Text(
            text = result.nativeText,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(end = 12.dp)
        )
    }
}

/** Shows why a card couldn't be saved. */
@Composable
private fun ErrorCard(message: String) {
    ContentCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Error, contentDescription = null, tint = CoralText)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
