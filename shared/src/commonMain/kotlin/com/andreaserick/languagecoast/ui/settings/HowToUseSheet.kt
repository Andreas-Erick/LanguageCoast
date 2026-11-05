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
        "Today",
        listOf(
            "Today is the whole routine: one short session a day on the coast you focus on. It reviews a few due cards (never the whole pile), then offers a few new sentences to keep or skip.",
            "Tap the flag line to say why you're learning the language: a trip, a person, a job, a show. The new sentences are picked for that reason, by your AI provider or from a built-in phrase bank. Say where you'll use it too (Mexico, Brazil, Switzerland): every translation then uses that place's words, forms and register.",
            "A kept sentence becomes a card and you practice it at the end of the session. A skipped one isn't offered again.",
            "After a few days away the session shrinks to a welcome-back size. Whatever a session leaves over waits for another day; \"Review all\" is there if you want it.",
            "\"Change\" next to the coast switches the focus coast.",
            "The basics come first: for about the first six weeks the new sentences come from the starter bank (greetings, getting by, small talk, about you, then the situations your goal points to), translated by your provider. After that your AI provider suggests sentences for your goal."
        )
    ),
    GuideSection(
        "Coasts and islands",
        listOfNotNull(
            "Each language you learn gets its own coast. Start one from My Coasts with \"New Coast\".",
            "On a coast, \"Starter topics\" adds a whole topic (Greetings, Numbers, Phone Settings, ...) as an island in one tap, translated for you in the background. With an AI provider, a native-speaker check then rewrites anything people wouldn't really say; the first version stays as an alternative and the note says why. Blanks like your name, city or job are filled in from Settings › About you (or asked for if you left them empty); sentences with other blanks stay in the Starter sentences list on Today. \"Add all\" adds every remaining topic, but a few topics at a time is the better pace.",
            "On a coast, your cards are grouped into islands by topic, like Travel or Restaurant.",
            ("The PDF icon at the top of a coast makes a practice sheet: your AI provider writes new sentences to translate from the words on that coast's cards, each about one island's topic and with at most one new word where it needs it. " +
                "Pick the direction, the difficulty and how many sentences; the answers come on a page of their own. Download the PDF to print it or fill it in.")
                .takeIf { fileTransferAvailable },
            "Set your native language here in Settings, so translations go the right way."
        )
    ),
    GuideSection(
        "Creating cards",
        listOf(
            "On Today, open \"Add your own\", type what you want to say in your own language and tap \"Translate & Save\".",
            "\"Starter sentences\" is a bank of about 650 sentences and words everyone needs, in 27 topics: greetings, getting by in the language, small talk, about me, restaurant, directions, airport, getting around, hotel, shopping, help and health, making plans, phone and internet, money, renting, paperwork, wishes, time, days and months, numbers (enough to build any number), everyday words, question words, family, colours, food and drink, signs, and the words your phone's Settings app uses once you switch it to the language. Tick what you'd say, fill in blanks like [city] or [name], and they become cards.",
            "The card goes to the focus coast shown at the top of Today.",
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
            "Today's session is the daily routine. Open a coast or an island from My Coasts to study just that. Spaced repetition decides when each card comes back.",
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
            "Not sure a card is right? \"Check translation\" in the ⋮ menu while studying has your AI provider read it as a native speaker would. Nothing changes unless you tap \"Use this\".",
            "Would you say it differently? In the same sheet, type your own translation under \"Correct as well?\". If your AI provider agrees, save it as an alternative: it then counts as correct when you type it.",
            "Export a coast to Anki, to a CSV file for a spreadsheet or to Markdown, or import cards from an Anki deck (.apkg) or a CSV file, under \"Your cards\" in Settings. " +
                "An Anki deck's notes become cards on islands named after its decks; if it has the foreign language on the front, turn on \"Swap sides\" in the preview."
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
