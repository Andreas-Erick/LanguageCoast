package com.andreaserick.languagecoast.ui.create

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.delay
import com.andreaserick.languagecoast.R
import com.andreaserick.languagecoast.data.Coast
import com.andreaserick.languagecoast.data.RecentCard
import com.andreaserick.languagecoast.ui.components.LanguagePickerDialog
import com.andreaserick.languagecoast.ui.components.ScreenHeader
import com.andreaserick.languagecoast.ui.components.plural
import com.andreaserick.languagecoast.ui.theme.CardWash
import com.andreaserick.languagecoast.ui.theme.CoralText
import com.andreaserick.languagecoast.ui.theme.DeepOceanBlue
import com.andreaserick.languagecoast.ui.theme.MistWhite
import com.andreaserick.languagecoast.ui.theme.SandBeige
import com.andreaserick.languagecoast.ui.theme.SandMuted
import com.andreaserick.languagecoast.ui.theme.WaveTeal

/**
 * The "Create" screen. Lets users create new flashcards from a native sentence, either
 * manually (providing their own translation and category) or with the translation provider picked
 * in Settings (on-device, Gemini or OpenRouter), which translates the sentence and, for the cloud
 * models, picks a category.
 */
@Composable
fun CreateScreen(
    onStartReview: (islandId: Int, islandName: String) -> Unit,
    viewModel: CreateViewModel = hiltViewModel()
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
                // The round emblem from the app icon; the full logo's wordmark is unreadable this small.
                Image(
                    painter = painterResource(id = R.mipmap.logo_android_language_coast_foreground),
                    contentDescription = null,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
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
                    .padding(horizontal = 24.dp, vertical = 16.dp)
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
                LaunchedEffect(uiState.result) {
                    if (uiState.result != null) {
                        delay(RESULT_SCROLL_DELAY_MILLIS)
                        resultRequester.bringIntoView()
                    }
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

/** The streak shown in the header, e.g. "🔥 4". */
@Composable
private fun StreakBadge(streak: Int) {
    Surface(
        shape = RoundedCornerShape(50),
        color = CardWash,
        contentColor = SandBeige,
        modifier = Modifier
            .padding(end = 16.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${plural(streak, "day")} streak" }
    ) {
        Text("🔥 $streak", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

/** A titled group on the home screen, styled like the cards in Settings. */
@Composable
private fun HomeCard(
    title: String,
    icon: ImageVector,
    trailing: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardWash, contentColor = Color.White),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                Icon(icon, contentDescription = null, tint = SandBeige, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = SandBeige, modifier = Modifier.weight(1f))
                trailing()
            }
            content()
        }
    }
}

/** How many cards are due today, with a button that starts reviewing them. */
@Composable
private fun TodayCard(dueCount: Int, dueCoastCount: Int, onStartReview: () -> Unit) {
    HomeCard(title = "Today", icon = Icons.Default.WbSunny) {
        Text(
            text = when {
                dueCount == 0 -> "All caught up 🌴 Nothing to review right now."
                dueCoastCount > 1 -> "${plural(dueCount, "card")} due on $dueCoastCount coasts"
                else -> "${plural(dueCount, "card")} due"
            },
            color = MistWhite
        )
        if (dueCount > 0) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onStartReview,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Start reviewing", fontSize = 16.sp)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

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
    HomeCard(
        title = "New card",
        icon = Icons.Default.EditNote,
        trailing = { CoastPicker(coasts = uiState.coasts, selected = selectedCoast, onSelected = onCoastSelected) }
    ) {
        if (uiState.isSameLanguage) {
            Text(
                text = sameLanguageMessage(selectedCoast.language),
                color = CoralText,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        ModeSelector(isManualMode = isManualMode, onManualModeChange = onManualModeChange)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = uiState.nativeSentence,
            onValueChange = onNativeSentenceChange,
            label = { Text("What do you want to say?") },
            placeholder = { Text("e.g., Where is the train station?") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (isManualMode) {
            OutlinedTextField(
                value = uiState.targetSentence,
                onValueChange = onTargetSentenceChange,
                label = { Text("${selectedCoast.language} Translation") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = uiState.category,
            onValueChange = onCategoryChange,
            // Manual cards fall back to DEFAULT_MANUAL_CATEGORY; the AI picks one otherwise.
            label = { Text("Category (Optional)") },
            placeholder = { Text("e.g., Travel") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !uiState.isSaving && uiState.nativeSentence.isNotBlank() && !uiState.isSameLanguage
        ) {
            if (uiState.isSaving) {
                // The button is disabled (dark) while saving, so the spinner needs a light color.
                CircularProgressIndicator(color = SandBeige)
            } else {
                val icon = if (isManualMode) Icons.Default.Save else Icons.Default.AutoAwesome
                val text = if (isManualMode) "Save Flashcard" else "Translate & Save"
                Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(text, fontSize = 18.sp)
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
            Text(selected.displayName, color = SandBeige, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = SandBeige)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = WaveTeal) {
            coasts.forEach { coast ->
                val isSelected = coast == selected
                DropdownMenuItem(
                    // White text on Wave Teal (5.3:1); selection is shown with a check, as in SelectionDropdown.
                    text = {
                        Text(
                            coast.displayName,
                            color = Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    trailingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White) }
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
    HomeCard(title = "Recently added", icon = Icons.Default.History) {
        cards.forEachIndexed { index, recent ->
            if (index > 0) HorizontalDivider(color = SandBeige.copy(alpha = 0.12f), modifier = Modifier.padding(vertical = 10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(recent.emoji, fontSize = 22.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(recent.card.nativeText, color = MistWhite, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        recent.card.targetText,
                        color = SandBeige,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(recent.islandName, color = MistWhite, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

/** Chooses between the translation provider translating the card and the user typing the translation themselves. */
@Composable
private fun ModeSelector(isManualMode: Boolean, onManualModeChange: (Boolean) -> Unit) {
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = SandBeige,
        activeContentColor = DeepOceanBlue,
        activeBorderColor = SandBeige,
        inactiveContainerColor = Color.Transparent,
        inactiveContentColor = SandBeige,
        inactiveBorderColor = SandMuted
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = !isManualMode,
            onClick = { onManualModeChange(false) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            colors = colors,
            icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp)) },
            label = { Text("AI translates") }
        )
        SegmentedButton(
            selected = isManualMode,
            onClick = { onManualModeChange(true) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            colors = colors,
            icon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
            label = { Text("I'll type it") }
        )
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
            .padding(24.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo_language_coast),
            contentDescription = "Language Coast",
            modifier = Modifier.size(160.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Welcome!",
            style = MaterialTheme.typography.headlineMedium,
            color = SandBeige
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Start a coast for the language you want to learn. You can add more coasts later.",
            color = SandBeige,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onStartCoast,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text("Start a Coast", fontSize = 18.sp)
        }
    }
}

/** Shows the card that was just saved, so a bad translation can be spotted and undone right away. */
@Composable
private fun SavedCard(result: SaveResult.Saved, onUndo: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = WaveTeal, contentColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(result.emoji, fontSize = 22.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Saved to ${result.category}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onUndo) {
                    Text("Undo", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(result.nativeText, fontSize = 15.sp, modifier = Modifier.padding(end = 12.dp))
            Text(
                text = "→ ${result.targetText}",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(end = 12.dp, top = 2.dp)
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DeepOceanBlue, contentColor = Color.White),
        border = BorderStroke(1.dp, CoralText),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Error, contentDescription = null, tint = CoralText)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = message, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}
