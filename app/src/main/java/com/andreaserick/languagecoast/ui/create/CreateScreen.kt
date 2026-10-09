package com.andreaserick.languagecoast.ui.create

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.andreaserick.languagecoast.ui.theme.CoralAccent

/**
 * The "Create" screen. Lets users create new flashcards from a native sentence, either
 * manually (providing their own translation and category) or via Google Gemini, which
 * translates the sentence and picks a category.
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
        onSave = viewModel::save
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
    onSave: () -> Unit
) {
    val isManualMode = uiState.isManualMode
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Image(
            painter = painterResource(id = R.drawable.logo_language_coast),
            contentDescription = "Language Coast Logo",
            modifier = Modifier.size(200.dp)
        )
        Spacer(modifier = Modifier.height(15.dp))

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
                color = CoralAccent,
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text("Manual Entry", color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = !isManualMode,
                onCheckedChange = { onManualModeChange(!it) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("AI", color = MaterialTheme.colorScheme.primary)
        }

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
                label = { Text("${selectedCoast?.language} Translation") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Spacer(modifier = Modifier.height(8.dp))
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

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !uiState.isSaving && uiState.nativeSentence.isNotBlank() && !uiState.isSameLanguage
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
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
            uiState.result?.let { ResultCard(it) }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** Shown instead of the form until the user has started their first coast. */
@Composable
private fun StartCoastPrompt(onStartCoast: () -> Unit) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        "Welcome! Start a coast for the language you want to learn. You can add more coasts later.",
        color = MaterialTheme.colorScheme.primary,
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

/** Feedback card shown after a save attempt. */
@Composable
private fun ResultCard(result: SaveResult) {
    val isError = result is SaveResult.Error
    val message = when (result) {
        is SaveResult.Saved -> if (result.manual) "Manually saved to ${result.category}!" else "AI saved to ${result.category}!"
        is SaveResult.Error -> "Error: ${result.message}"
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isError) Icons.Default.Error else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }
    }
}
