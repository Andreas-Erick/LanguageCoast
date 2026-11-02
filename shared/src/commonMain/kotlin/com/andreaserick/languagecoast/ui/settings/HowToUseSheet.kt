package com.andreaserick.languagecoast.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.andreaserick.languagecoast.data.localAiAvailable
import com.andreaserick.languagecoast.data.onDeviceTranslationAvailable
import com.andreaserick.languagecoast.ui.theme.OceanGround
import com.andreaserick.languagecoast.ui.theme.SurfaceRaised
import com.andreaserick.languagecoast.ui.theme.TextSecondary

/** One topic of the guide: a title and a few short tips. */
private class GuideSection(val title: String, val tips: List<String>)

private val GUIDE = listOf(
    GuideSection(
        "Coasts and islands",
        listOf(
            "Each language you learn gets its own coast. Start one from My Coasts with \"New Coast\".",
            "On a coast, your cards are grouped into islands by topic, like Travel or Restaurant.",
            "Set your native language here in Settings, so translations go the right way."
        )
    ),
    GuideSection(
        "Creating cards",
        listOf(
            "On Create, type what you want to say in your own language and tap \"Translate & Save\".",
            "Tap the coast name to pick which coast the card goes to.",
            "Choose \"I write it\" to write in the language you're learning: the AI fills in your language and corrects slips. \"Manual\" lets you type both sides.",
            "\"Add many\" turns a pasted paragraph or a photo of a page into sentences to tick, one card each.",
            "Tap an existing island chip or type a category to choose the island yourself. Leave it empty and your AI provider picks one for you. While you type, a likely island is suggested.",
            "If the coast already has that sentence, a line under the text box says which island it is on.",
            "Made a mistake? Tap \"Undo\" right after saving."
        )
    ),
    GuideSection(
        "AI & Translation Providers",
        listOfNotNull(
            "On-device translation is free, private and works offline. It needs no setup."
                .takeIf { onDeviceTranslationAvailable },
            "Local AI scans your phone's RAM, storage, CPU and GPU, recommends models that fit (such as Qwen 3, LFM2.5 and Gemma 4), and downloads the one you pick to run 100% offline with automatic island categorization. Gemma 3 is gated on Hugging Face and needs a token from there."
                .takeIf { localAiAvailable },
            "Cloud AI providers (Google Gemini, OpenAI, Anthropic Claude, Groq, DeepSeek and OpenRouter) give rich translations, sort cards into islands and suggest natural alternatives.",
            "For longer sentences, AI models also suggest other ways to say it and explain nuances."
        )
    ),
    GuideSection(
        "Studying",
        listOf(
            "\"Review\" on Create studies everything due today across your coasts. Open a coast or an island to study just that. Spaced repetition decides when each card comes back.",
            "Flip: tap \"Show answer\", then grade yourself with Again, Hard, Good or Easy. " +
                "You can also swipe left for Again and right for Good.",
            "Type: type the translation and tap \"Check answer\". Capitals and punctuation don't matter, accents do. A slip of a letter counts as Hard.",
            "Fill gap: the translation has its key word blanked out. Type the missing word.",
            "Leaky words on My Coasts gathers the cards you keep forgetting into one short session.",
            "Tap the languages under the switch (e.g. \"English → German\") to study the other way round: you see the translation and answer in your own language.",
            "The ⋮ menu during a session has Previous card, Skip card and all of the island's cards.",
            "Nothing due? You can still practice all cards of the island."
        )
    ),
    GuideSection(
        "On the back of a card",
        listOf(
            "Tap the speaker to hear the translation read aloud.",
            "Tap any word to have your AI provider explain it, with a dict.cc link alongside.",
            "If a card has alternatives, tap \"alternatives\" to see other ways to say it, and \"Make main\" to swap one in."
        )
    ),
    GuideSection(
        "Streaks and reminders",
        listOfNotNull(
            "Finish an island each day to keep your streak going. One missed day a week is covered by a streak freeze.",
            "Pick a weekly island goal here in Settings; My Coasts shows how far along you are. The recap icon there sums up your month.",
            "Turn on the daily reminder here in Settings, at a fixed time or a surprise time. A quoted card can be revealed and graded from the notification.",
            "Add the Language Coast widget to your home screen to see your streak and the cards due today."
                .takeIf { homeScreenWidgetAvailable }
        )
    ),
    GuideSection(
        "Your cards",
        listOfNotNull(
            "Tap the search icon on My Coasts to find a card on any coast by its native text, translation or alternatives.",
            "Tap ⋮ on an island and \"See cards\" to list its cards with when each is due next.",
            "Tap a card there, or \"Edit card\" in the ⋮ menu while studying, to fix its translation and alternatives or move it to another island.",
            "Export a coast to Anki or Markdown, or import cards from a CSV file, under \"Your cards\" in Settings."
                .takeIf { fileTransferAvailable },
            "Changing phones? \"Back up\" under Backup in Settings saves everything, including your progress and streak. \"Restore\" it on the new phone."
                .takeIf { fileTransferAvailable },
            "Deleted a coast, island or card by accident? Tap \"Undo\" in the message at the bottom."
        )
    )
)

/**
 * A bottom sheet explaining how to use the app, one short section per topic.
 * It scrolls when the content is long.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowToUseSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = OceanGround) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            Text("How to use Language Coast", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Build your coast, one word at a time.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            GUIDE.forEach { section ->
                HorizontalDivider(color = SurfaceRaised, modifier = Modifier.padding(vertical = 12.dp))
                Text(section.title, style = MaterialTheme.typography.titleMedium)
                section.tips.forEach { tip ->
                    Row(modifier = Modifier.padding(top = 6.dp)) {
                        Text("•", color = TextSecondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(tip, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }
            }
        }
    }
}
