package com.andreaserick.languagecoast.ui.screens

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andreaserick.languagecoast.R
import com.andreaserick.languagecoast.data.AiTranslator
import com.andreaserick.languagecoast.data.AppDatabase
import com.andreaserick.languagecoast.data.Flashcard
import com.andreaserick.languagecoast.data.LanguageIsland
import com.andreaserick.languagecoast.data.SettingsManager
import kotlinx.coroutines.launch

/** Category used for manual cards when the user leaves the category blank. */
private const val DEFAULT_MANUAL_CATEGORY = "My Words"

/**
 * The "Create" screen. Lets users create new flashcards from a native sentence, either
 * manually (providing their own translation and category) or via Google Gemini, which
 * translates the sentence and picks a category.
 */
@Composable
fun CreateScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val dao = remember { AppDatabase.getDatabase(context).languageCoastDao() }
    val settingsManager = remember { SettingsManager(context) }

    val userApiKey by settingsManager.apiKeyFlow.collectAsState(initial = "")
    val aiTranslator = remember(userApiKey) {
        if (userApiKey.isNotBlank()) AiTranslator(userApiKey) else null
    }

    val targetLang by settingsManager.targetLanguageFlow.collectAsState(initial = SettingsManager.DEFAULT_TARGET_LANGUAGE)
    val nativeLang by settingsManager.nativeLanguageFlow.collectAsState(initial = SettingsManager.DEFAULT_NATIVE_LANGUAGE)
    val geminiModel by settingsManager.geminiModelFlow.collectAsState(initial = SettingsManager.DEFAULT_GEMINI_MODEL)
    val islands by dao.getAllIslands().collectAsState(initial = emptyList())

    var nativeSentence by remember { mutableStateOf("") }
    var targetSentence by remember { mutableStateOf("") } // Only used in manual mode
    var category by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf("") }
    var isManualMode by remember { mutableStateOf(false) }

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

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text("Manual Entry", color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = !isManualMode,
                onCheckedChange = { isManualMode = !it }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("AI", color = MaterialTheme.colorScheme.primary)
        }

        OutlinedTextField(
            value = nativeSentence,
            onValueChange = { nativeSentence = it },
            label = { Text("What do you want to say?") },
            placeholder = { Text("e.g., Where is the train station?") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isManualMode) {
            OutlinedTextField(
                value = targetSentence,
                onValueChange = { targetSentence = it },
                label = { Text("Target Translation") },
                placeholder = { Text("e.g., ¿Dónde está la estación de tren?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = category,
            onValueChange = { category = it },
            // Manual cards fall back to DEFAULT_MANUAL_CATEGORY; the AI picks one otherwise.
            label = { Text("Category (Optional)") },
            placeholder = { Text("e.g., Travel") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (nativeSentence.isBlank()) return@Button
                isLoading = true
                lastResult = ""

                // Room and AiTranslator are main-safe, so this runs on the main dispatcher
                // and Compose state can be updated directly.
                coroutineScope.launch {
                    try {
                        val finalCategory: String
                        val finalTargetText: String

                        if (isManualMode) {
                            require(targetSentence.isNotBlank()) { "Translation cannot be empty in manual mode." }
                            finalCategory = category.trim().ifBlank { DEFAULT_MANUAL_CATEGORY }
                            finalTargetText = targetSentence.trim()
                        } else {
                            requireNotNull(aiTranslator) { "Please enter an API Key in Settings first!" }

                            val result = aiTranslator.translateAndCategorize(
                                nativeSentence = nativeSentence,
                                targetLanguage = targetLang,
                                nativeLanguage = nativeLang,
                                userCategory = category,
                                existingCategories = islands.map { it.name },
                                modelName = geminiModel
                            )
                            check(result.isSuccess) { "AI Translation Failed." }

                            finalCategory = result.finalCategory
                            finalTargetText = result.translatedText
                        }

                        // Reuse the island with this name, or create it.
                        val islandId = dao.getIslandByName(finalCategory)?.islandId
                            ?: dao.insertIsland(LanguageIsland(name = finalCategory)).toInt()

                        dao.insertFlashcard(
                            Flashcard(
                                islandId = islandId,
                                nativeText = nativeSentence.trim(),
                                targetText = finalTargetText
                            )
                        )

                        lastResult = if (isManualMode) "Manually saved to $finalCategory!" else "AI saved to $finalCategory!"
                        nativeSentence = ""
                        targetSentence = ""
                        category = ""
                    } catch (e: Exception) {
                        lastResult = "Error: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
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
            visible = lastResult.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            ResultCard(message = lastResult, isError = lastResult.startsWith("Error"))
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** Feedback card shown after a save attempt. */
@Composable
private fun ResultCard(message: String, isError: Boolean) {
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
