package com.andreaserick.languagecoast.ui.study

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andreaserick.languagecoast.data.Flashcard
import com.andreaserick.languagecoast.ui.theme.CoralAccent
import com.andreaserick.languagecoast.ui.theme.DeepOceanBlue
import com.andreaserick.languagecoast.ui.theme.SandBeige
import com.andreaserick.languagecoast.ui.theme.WaveTeal
import com.andreaserick.languagecoast.util.dictCcSearchUrl

/**
 * The main screen for studying flashcards within a specific "Language Island" (category).
 * Provides two modes of study: "Flip Cards" (standard flashcard) and "Active Type" (writing practice).
 * The island is taken from the navigation arguments by [StudyViewModel].
 *
 * @param onNavigateBack Callback to navigate back to the previous screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(onNavigateBack: () -> Unit, viewModel: StudyViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentCard = uiState.currentCard

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.islandName, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (currentCard != null) {
                        IconButton(onClick = viewModel::deleteCurrentCard) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Card",
                                tint = CoralAccent
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepOceanBlue,
                    titleContentColor = SandBeige,
                    navigationIconContentColor = SandBeige
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            when {
                uiState.isLoading -> Unit
                !uiState.hasCards -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No flashcards here yet!", color = SandBeige)
                }
                uiState.isSessionComplete -> SessionCompleteView(
                    onRestart = viewModel::restart,
                    onBack = onNavigateBack
                )
                currentCard != null -> {
                    StudyModeTabs(isTypingMode = uiState.isTypingMode, onModeChange = viewModel::setTypingMode)

                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Card ${uiState.currentIndex + 1} of ${uiState.sessionCards.size}",
                            color = SandBeige.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // key(cardId) resets per-card state (flip state, typed answer)
                        // when we move to a different card.
                        key(currentCard.cardId) {
                            if (uiState.isTypingMode) {
                                TypeStudyView(currentCard = currentCard)
                            } else {
                                FlipStudyView(
                                    currentCard = currentCard,
                                    nativeLang = uiState.nativeLanguage,
                                    targetLang = uiState.targetLanguage,
                                    onAgain = viewModel::onAgain,
                                    onEasy = viewModel::onEasy
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = viewModel::previous,
                            enabled = uiState.canGoBack,
                            colors = ButtonDefaults.buttonColors(containerColor = SandBeige, contentColor = DeepOceanBlue)
                        ) { Text("Previous") }

                        Button(
                            onClick = viewModel::next,
                            enabled = uiState.canGoForward,
                            colors = ButtonDefaults.buttonColors(containerColor = SandBeige, contentColor = DeepOceanBlue)
                        ) { Text("Next") }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyModeTabs(isTypingMode: Boolean, onModeChange: (Boolean) -> Unit) {
    PrimaryTabRow(
        selectedTabIndex = if (isTypingMode) 1 else 0,
        containerColor = DeepOceanBlue,
        contentColor = SandBeige,
        divider = {}
    ) {
        Tab(
            selected = !isTypingMode,
            onClick = { onModeChange(false) },
            text = { Text("Flip Cards", fontWeight = FontWeight.SemiBold) },
            selectedContentColor = SandBeige,
            unselectedContentColor = SandBeige.copy(alpha = 0.6f)
        )
        Tab(
            selected = isTypingMode,
            onClick = { onModeChange(true) },
            text = { Text("Active Type", fontWeight = FontWeight.SemiBold) },
            selectedContentColor = SandBeige,
            unselectedContentColor = SandBeige.copy(alpha = 0.6f)
        )
    }
}

/**
 * A flashcard view that flips when clicked, revealing the translation.
 * Words on the "back" side are clickable to trigger a dictionary search.
 *
 * @param currentCard The [Flashcard] data to display.
 * @param nativeLang The user's native language name.
 * @param targetLang The language being studied.
 * @param onAgain Callback when the user clicks "Again".
 * @param onEasy Callback when the user clicks "Easy".
 */
@Composable
fun FlipStudyView(
    currentCard: Flashcard,
    nativeLang: String,
    targetLang: String,
    onAgain: () -> Unit,
    onEasy: () -> Unit
) {
    var isFlipped by remember(currentCard) { mutableStateOf(false) }
    val context = LocalContext.current

    // Animate the card rotation
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "flip"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 12f * density
                }
                .clickable { isFlipped = !isFlipped },
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isFlipped) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // Keep content upright regardless of the card's rotation
                Box(modifier = Modifier.graphicsLayer { rotationY = if (isFlipped) 180f else 0f }) {
                    if (isFlipped) {
                        ClickableWordSentence(
                            sentence = currentCard.targetText,
                            onWordClick = { word ->
                                val url = dictCcSearchUrl(word, nativeLang, targetLang)
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                                } catch (e: ActivityNotFoundException) {
                                    Toast.makeText(context, "No browser found to open dict.cc", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    } else {
                        Text(
                            text = currentCard.nativeText,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }

        if (isFlipped) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        isFlipped = false // Explicitly unflip before triggering logic
                        onAgain()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralAccent)
                ) {
                    Text("Again")
                }
                Button(
                    onClick = onEasy,
                    colors = ButtonDefaults.buttonColors(containerColor = WaveTeal)
                ) {
                    Text("Easy")
                }
            }
        }
    }
}

/**
 * A study view that requires the user to type the translation of the native text.
 * Provides immediate feedback on correctness.
 *
 * @param currentCard The [Flashcard] data to test against.
 */
@Composable
fun TypeStudyView(currentCard: Flashcard) {
    var userInput by remember(currentCard) { mutableStateOf("") }
    var hasChecked by remember(currentCard) { mutableStateOf(false) }
    var isCorrect by remember(currentCard) { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Translate this:", color = MaterialTheme.colorScheme.secondary)

        Text(
            text = currentCard.nativeText,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )

        OutlinedTextField(
            value = userInput,
            onValueChange = {
                userInput = it
                hasChecked = false // Hide feedback if they start typing again
            },
            label = { Text("Type the translation...") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                // Perform a simple case-insensitive comparison
                isCorrect = userInput.trim().equals(currentCard.targetText.trim(), ignoreCase = true)
                hasChecked = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Check Answer")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Display feedback results
        if (hasChecked) {
            if (isCorrect) {
                Text("✅ Perfect!", color = MaterialTheme.colorScheme.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("❌ Not quite.", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                        Text("Correct answer:", color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(top = 8.dp))
                        Text(currentCard.targetText, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * A view displayed when the user has completed their study session.
 * Provides options to restart the session or return to the main screen.
 *
 * @param onRestart Callback to restart the study session.
 * @param onBack Callback to navigate back to the previous screen.
 */
@Composable
fun SessionCompleteView(onRestart: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Island Explored!",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = SandBeige,
            textAlign = TextAlign.Center
        )
        Text(
            text = "You've mastered all cards in this session.",
            fontSize = 18.sp,
            color = SandBeige.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 48.dp)
        )

        Button(
            onClick = onRestart,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = WaveTeal)
        ) {
            Text("Study Again", fontSize = 18.sp, modifier = Modifier.padding(8.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, SandBeige),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = SandBeige)
        ) {
            Text("Back to Islands", fontSize = 18.sp, modifier = Modifier.padding(8.dp))
        }
    }
}

/**
 * Renders a sentence where each word is individually clickable.
 * Useful for looking up specific words in a dictionary.
 *
 * @param sentence The full sentence to display.
 * @param onWordClick Callback triggered when a word is clicked, passing the sanitized word.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClickableWordSentence(sentence: String, onWordClick: (String) -> Unit) {
    // FlowRow automatically wraps words to the next line
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        val words = sentence.split(" ")

        words.forEach { word ->
            Text(
                text = "$word ", // Space included for visual separation
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clickable {
                        // Strip punctuation so we search for just the word (e.g., "hola!" -> "hola")
                        val cleanWord = word.replace(Regex("[^\\p{L}\\p{Nd}]+"), "")
                        if (cleanWord.isNotBlank()) {
                            onWordClick(cleanWord)
                        }
                    }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            )
        }
    }
}
