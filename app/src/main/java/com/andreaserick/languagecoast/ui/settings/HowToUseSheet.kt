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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andreaserick.languagecoast.ui.theme.DeepOceanBlue
import com.andreaserick.languagecoast.ui.theme.MistWhite
import com.andreaserick.languagecoast.ui.theme.SandBeige

/** One topic of the guide: an emoji, a title and a few short tips. */
private class GuideSection(val emoji: String, val title: String, val tips: List<String>)

private val GUIDE = listOf(
    GuideSection(
        "🏝️", "Coasts and islands",
        listOf(
            "Each language you learn gets its own coast. Start one from My Coasts with \"New Coast\".",
            "On a coast, your cards are grouped into islands by topic, like Travel or Restaurant.",
            "Set your native language here in Settings, so translations go the right way."
        )
    ),
    GuideSection(
        "✍️", "Creating cards",
        listOf(
            "On Create, type what you want to say in your own language and tap \"Translate & Save\".",
            "Tap the coast name to pick which coast the card goes to.",
            "Choose \"I'll type it\" to enter the translation yourself instead.",
            "Type a category to choose the island yourself. Leave it empty and Gemini or OpenRouter pick one for you.",
            "If the coast already has that sentence, a line under the text box says which island it is on.",
            "Made a mistake? Tap \"Undo\" right after saving."
        )
    ),
    GuideSection(
        "🤖", "Translation",
        listOf(
            "On-device translation is free, private and works offline. It needs no setup.",
            "Gemini and OpenRouter give more natural translations and sort cards into islands for you. " +
                "They need an API key, which you add here in Settings.",
            "For longer sentences, they also suggest other ways to say it."
        )
    ),
    GuideSection(
        "🧠", "Studying",
        listOf(
            "Open an island to study the cards that are due today. Spaced repetition decides when each card comes back.",
            "Flip Cards: tap \"Show answer\", then grade yourself with Again, Hard, Good or Easy. " +
                "You can also swipe left for Again and right for Good.",
            "Active Type: type the translation and tap \"Check answer\". Capitals and punctuation don't matter, accents do.",
            "Tap the languages under the tabs (e.g. \"English → German\") to study the other way round: you see the translation and answer in your own language.",
            "Nothing due? You can still practice all cards of the island."
        )
    ),
    GuideSection(
        "🔊", "On the back of a card",
        listOf(
            "Tap the speaker to hear the translation read aloud.",
            "Tap any word to look it up in the dict.cc dictionary.",
            "If a card has alternatives, tap \"alternatives\" to see other ways to say it, and \"Make main\" to swap one in."
        )
    ),
    GuideSection(
        "🔥", "Streaks and reminders",
        listOf(
            "Finish an island each day to keep your streak going.",
            "Turn on the daily reminder here in Settings, at a fixed time or a surprise time.",
            "Add the Language Coast widget to your home screen to see your streak and the cards due today."
        )
    ),
    GuideSection(
        "📤", "Your cards",
        listOf(
            "Tap the search icon on My Coasts to find a card on any coast by its native text, translation or alternatives.",
            "Tap ⋮ on an island and \"See cards\" to list its cards with when each is due next.",
            "Tap a card there, or \"Edit card\" in the ⋮ menu while studying, to fix its translation and alternatives or move it to another island.",
            "Export a coast to Anki or Markdown, or import cards from a CSV file, under \"Your cards\" in Settings.",
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DeepOceanBlue, contentColor = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            Text("How to use Language Coast", style = MaterialTheme.typography.titleLarge, color = SandBeige)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Build your coast, one word at a time.", color = MistWhite, fontSize = 14.sp)
            GUIDE.forEach { section ->
                HorizontalDivider(color = SandBeige.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 12.dp))
                Text(
                    "${section.emoji}  ${section.title}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = SandBeige
                )
                section.tips.forEach { tip ->
                    Row(modifier = Modifier.padding(top = 6.dp)) {
                        Text("•", color = MistWhite)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(tip, color = Color.White, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
