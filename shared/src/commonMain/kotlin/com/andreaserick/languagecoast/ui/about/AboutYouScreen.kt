package com.andreaserick.languagecoast.ui.about

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andreaserick.languagecoast.data.PERSONAL_ISLAND_SENTENCES
import com.andreaserick.languagecoast.data.PersonalIsland
import com.andreaserick.languagecoast.data.PersonalIslands
import com.andreaserick.languagecoast.data.Profile
import com.andreaserick.languagecoast.data.TranslationProvider
import com.andreaserick.languagecoast.data.Wording
import com.andreaserick.languagecoast.data.localAiAvailable
import com.andreaserick.languagecoast.data.profileExample
import com.andreaserick.languagecoast.data.profilePreview
import com.andreaserick.languagecoast.data.profileShortLabel
import com.andreaserick.languagecoast.ui.components.CoastTextField
import com.andreaserick.languagecoast.ui.components.coastChipBorder
import com.andreaserick.languagecoast.ui.components.coastChipColors
import com.andreaserick.languagecoast.ui.components.ContentCard
import com.andreaserick.languagecoast.ui.components.IconTile
import com.andreaserick.languagecoast.ui.components.Pill
import com.andreaserick.languagecoast.ui.components.ScreenHeader
import com.andreaserick.languagecoast.ui.components.ScreenMargin
import com.andreaserick.languagecoast.ui.settings.FREE_GEMINI_KEY_WARNING
import com.andreaserick.languagecoast.ui.theme.OnSeafoam
import com.andreaserick.languagecoast.ui.theme.Seafoam
import com.andreaserick.languagecoast.ui.theme.SurfaceRaised
import com.andreaserick.languagecoast.ui.theme.SurfaceSelected
import com.andreaserick.languagecoast.ui.theme.SunlitText
import com.andreaserick.languagecoast.ui.theme.TextPrimary
import com.andreaserick.languagecoast.ui.theme.TextSecondary
import org.koin.compose.viewmodel.koinViewModel

/** List items before the first island: the introduction and the basics. */
private const val ITEMS_BEFORE_ISLANDS = 2

/**
 * "About you": the basics starter sentences are filled in with, and under them one card of questions per
 * island about the user ([PersonalIslands]). Everything is optional and saved as it is typed. On a coast,
 * the AI provider then writes an island from each set of answers.
 *
 * @param openAt The [PersonalIsland.id] whose questions to open and scroll to, or empty.
 */
@Composable
fun AboutYouScreen(
    openAt: String,
    onNavigateBack: () -> Unit,
    viewModel: AboutYouViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var open by rememberSaveable { mutableStateOf(openAt) }

    LaunchedEffect(openAt) {
        val index = PersonalIslands.ALL.indexOfFirst { it.id == openAt }
        if (index >= 0) listState.scrollToItem(index + ITEMS_BEFORE_ISLANDS)
    }

    // App.kt already applies imePadding() around NavHost, so avoid double IME insets here.
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "About you",
            subtitle = if (uiState.isLoading) null else "${uiState.answeredCount} of ${uiState.questionCount} answered",
            onBack = onNavigateBack
        )
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = ScreenMargin, end = ScreenMargin, top = 12.dp, bottom = 24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "intro") { Introduction(uiState.provider, uiState.engineProblem) }
            item(key = "basics") {
                BasicsCard(profile = uiState.profile, wording = uiState.wording, onChange = viewModel::setProfileField, onWording = viewModel::setWording)
            }
            items(PersonalIslands.ALL, key = { it.id }) { island ->
                IslandQuestionsCard(
                    island = island,
                    answers = uiState.answers,
                    expanded = open == island.id,
                    onToggle = { open = if (open == island.id) "" else island.id },
                    onAnswer = viewModel::setAnswer
                )
            }
        }
    }
}

/**
 * What the questions are for, and where the answers go. When the provider picked in Settings can't write
 * islands (on-device translation never can; a cloud provider can't before it is set up, see [engineProblem]),
 * a card says so before the user starts answering.
 */
@Composable
private fun Introduction(provider: TranslationProvider, engineProblem: String?) {
    val where = when {
        provider.isCloud -> "When you have an island written, the answers for that island are sent to ${provider.label}, your AI provider."
        provider == TranslationProvider.LocalAi -> "Your AI model runs on this device, so they are not sent to an AI provider. A backup of your phone includes them."
        else -> "If you pick a cloud provider, the answers for an island are sent to it when you have that island written."
    }
    // On-device translation only exists where Local AI may too, so both can be named here.
    val cannotWrite = when {
        provider == TranslationProvider.OnDevice ->
            "Islands about you are written by an AI model, and on-device translation can't write them. " +
                (if (localAiAvailable) "Set up Local AI, which is free and runs on this device, or a cloud provider" else "Set up a cloud provider") +
                " under Settings › Translation. The basics below still fill in your starter sentences."
        else -> engineProblem
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Most of what you say in a new language is about you. Answer in a few words, in any language, and on each coast your AI provider writes up to $PERSONAL_ISLAND_SENTENCES sentences for each island: your family, your work, your weekends, in your words.",
            style = MaterialTheme.typography.bodyMedium
        )
        if (cannotWrite != null) {
            ContentCard(modifier = Modifier.padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    IconTile { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = TextSecondary) }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Before islands can be written", style = MaterialTheme.typography.titleSmall)
                        Text(cannotWrite, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
        Text(
            "Everything is optional. Your answers are stored on this device. $where",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        if (provider == TranslationProvider.Gemini) {
            // Google's terms for keys that cost nothing allow this; the paid ones, and all keys in those countries, don't.
            Text(FREE_GEMINI_KEY_WARNING, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

/** The six basics (name, job, where the user lives and comes from) with how starter sentences will read. */
@Composable
private fun BasicsCard(
    profile: Profile,
    wording: Wording,
    onChange: (placeholder: String, value: String) -> Unit,
    onWording: (Wording) -> Unit
) {
    val fillers = profile.fillers()
    ContentCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile { Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary) }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("The basics", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text("These fill the blanks in starter sentences, like [name] and [city].", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }

        @Composable
        fun Pair(heading: String, first: String, second: String) {
            Text(heading, style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(first, second).forEach { placeholder ->
                    DraftField(
                        saved = fillers[placeholder].orEmpty(),
                        onChange = { onChange(placeholder, it) },
                        label = profileShortLabel(placeholder),
                        example = profileExample(placeholder),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Pair("You", "name", "job")
        Pair("Where you live", "city", "country")
        Pair("Where you're from", "home country", "hometown")

        Text("How sentences about you are worded", style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf(Wording.Feminine, Wording.Masculine, Wording.Neutral).forEach { option ->
                FilterChip(
                    selected = option == wording,
                    onClick = { onWording(option) },
                    label = { Text(option.label, style = MaterialTheme.typography.labelMedium) },
                    colors = coastChipColors(),
                    border = coastChipBorder(enabled = true, selected = option == wording),
                    shape = MaterialTheme.shapes.small
                )
            }
        }
        Text(
            text = "Many languages say \"I'm tired\" or \"I'm a nurse\" differently for a woman and a man. Pick one and your sentences come out right; pick it again to clear it.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )

        profilePreview(profile)?.let { preview ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .background(SurfaceRaised, MaterialTheme.shapes.small)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text("How your sentences will read", style = MaterialTheme.typography.labelMedium, color = SunlitText)
                Text(preview, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/**
 * The questions behind one island about the user. Closed, it shows the island and how many of its
 * questions are answered; open, a field per question.
 */
@Composable
private fun IslandQuestionsCard(
    island: PersonalIsland,
    answers: Map<String, String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAnswer: (key: String, value: String) -> Unit
) {
    val answered = island.answered(answers).size
    ContentCard(padding = PaddingValues(0.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = if (expanded) "Close ${island.name}" else "Open ${island.name}", role = Role.Button, onClick = onToggle)
                .padding(start = 16.dp, top = 14.dp, end = 12.dp, bottom = 14.dp)
        ) {
            IconTile { Text(island.emoji, fontSize = 22.sp) }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(island.name, style = MaterialTheme.typography.titleMedium)
                Text(island.about, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            // Always there, so the first answer doesn't push the text beside it onto another line.
            when (answered) {
                0 -> Pill("0/${island.questions.size}", container = SurfaceRaised, content = TextSecondary)
                island.questions.size -> Pill("$answered/${island.questions.size}", container = Seafoam, content = OnSeafoam)
                else -> Pill("$answered/${island.questions.size}", container = SurfaceSelected, content = TextPrimary)
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.padding(start = 4.dp).size(24.dp)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 18.dp)
            ) {
                island.questions.forEach { question ->
                    Column {
                        Text(question.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 6.dp))
                        DraftField(
                            saved = answers[question.key].orEmpty(),
                            onChange = { onAnswer(question.key, it) },
                            label = null,
                            example = question.example,
                            singleLine = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * A text field that saves as it is typed. Typing is kept locally so the field doesn't lag behind the
 * saved value. Until the user types, the field follows what is saved (which arrives from disk after the
 * first composition); after that, saved values only echo the user's own keystrokes and are ignored, so a
 * slow write can't eat a letter.
 */
@Composable
private fun DraftField(
    saved: String,
    onChange: (String) -> Unit,
    label: String?,
    example: String,
    singleLine: Boolean,
    modifier: Modifier = Modifier
) {
    var draft by remember { mutableStateOf(saved) }
    var typed by remember { mutableStateOf(false) }
    LaunchedEffect(saved) { if (!typed) draft = saved }
    CoastTextField(
        value = draft,
        onValueChange = {
            typed = true
            draft = it
            onChange(it)
        },
        label = label?.let { { Text(it) } },
        placeholder = { Text(example) },
        // The basics go into sentences as typed ("I work as a nurse"), so the keyboard mustn't capitalize them.
        keyboardOptions = if (singleLine) KeyboardOptions.Default else KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        singleLine = singleLine,
        modifier = modifier
    )
}
