package com.andreaserick.languagecoast.ui.create

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.andreaserick.languagecoast.R
import com.andreaserick.languagecoast.data.Coast
import com.andreaserick.languagecoast.ui.components.LanguagePickerDialog
import com.andreaserick.languagecoast.ui.components.SelectionDropdown
import com.andreaserick.languagecoast.ui.theme.CoralText
import com.andreaserick.languagecoast.ui.theme.DeepOceanBlue
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
fun CreateScreen(viewModel: CreateViewModel = hiltViewModel()) {
    CreateContent(
        uiState = viewModel.uiState,
        onNativeSentenceChange = viewModel::onNativeSentenceChange,
        onTargetSentenceChange = viewModel::onTargetSentenceChange,
        onCategoryChange = viewModel::onCategoryChange,
        onManualModeChange = viewModel::onManualModeChange,
        onCoastSelected = viewModel::onCoastSelected,
        onAddCoast = viewModel::addCoast,
        onSave = viewModel::save,
        onUndo = viewModel::undoLastSave
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
    onUndo: () -> Unit
) {
    val isManualMode = uiState.isManualMode
    var showNewCoastDialog by remember { mutableStateOf(false) }
    // The big logo welcomes new users; once they have a coast it shrinks to make room for the form.
    val logoSize by animateDpAsState(if (uiState.coasts.isEmpty()) 200.dp else 96.dp, label = "logo")

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Image(
            painter = painterResource(id = R.drawable.logo_language_coast),
            contentDescription = "Language Coast",
            modifier = Modifier.size(logoSize)
        )
        Spacer(modifier = Modifier.height(16.dp))

        val selectedCoast = uiState.selectedCoast
        when {
            uiState.isLoadingCoasts -> return@Column
            selectedCoast == null -> {
                StartCoastPrompt(onStartCoast = { showNewCoastDialog = true })
                return@Column
            }
        }

        SelectionDropdown(
            options = uiState.coasts,
            selected = selectedCoast,
            onSelected = onCoastSelected,
            optionLabel = { it.displayName },
            label = "Adding to",
            modifier = Modifier.fillMaxWidth()
        )

        if (uiState.isSameLanguage) {
            Text(
                text = sameLanguageMessage(selectedCoast.language),
                color = CoralText,
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        ModeSelector(isManualMode = isManualMode, onManualModeChange = onManualModeChange)

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = uiState.nativeSentence,
            onValueChange = onNativeSentenceChange,
            label = { Text("What do you want to say?") },
            placeholder = { Text("e.g., Where is the train station?") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isManualMode) {
            OutlinedTextField(
                value = uiState.targetSentence,
                onValueChange = onTargetSentenceChange,
                label = { Text("${selectedCoast.language} Translation") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Spacer(modifier = Modifier.height(16.dp))
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

        Spacer(modifier = Modifier.height(28.dp))

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

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedVisibility(
            visible = uiState.result != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            when (val result = uiState.result) {
                is SaveResult.Saved -> SavedCard(result, onUndo = onUndo)
                is SaveResult.Error -> ErrorCard(result.message)
                null -> Unit
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
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

/** Shown instead of the form until the user has started their first coast. */
@Composable
private fun StartCoastPrompt(onStartCoast: () -> Unit) {
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

/** Shows the card that was just saved, so a bad translation can be spotted and undone right away. */
@Composable
private fun SavedCard(result: SaveResult.Saved, onUndo: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = WaveTeal, contentColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Error, contentDescription = null, tint = CoralText)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = message, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}
