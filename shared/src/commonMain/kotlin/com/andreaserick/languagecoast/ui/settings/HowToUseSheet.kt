package com.andreaserick.languagecoast.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.andreaserick.languagecoast.data.localAiAvailable
import com.andreaserick.languagecoast.data.onDeviceTranslationAvailable
import com.andreaserick.languagecoast.ui.components.Support
import com.andreaserick.languagecoast.ui.theme.SurfaceRaised
import com.andreaserick.languagecoast.ui.theme.TextSecondary

/** One topic of the guide: a title and a few short tips. */
private class GuideSection(val title: String, val tips: List<String>)

/*
 * The guide. A tip is two or three sentences about one thing, and quotes a button by the words on it, so it
 * has to change when the button does. A tip about something only one platform has is left out on the other
 * with takeIf: put the whole text in parentheses first, or takeIf applies to the last piece only and the
 * tip ends in "null".
 */
private val GUIDE = listOf(
    GuideSection(
        "Today",
        listOf(
            "Today is the whole routine: one short session a day on the coast you focus on. It reviews a few due cards (never the whole pile), then offers a few new sentences to keep or skip.",
            "Tap the flag line to say why you're learning the language: a trip, a person, a job, a show. The new sentences are picked for that reason.",
            "Say where you'll use the language too (Mexico, Brazil, Switzerland): every translation then uses that place's words and forms.",
            "A kept sentence becomes a card and you practice it at the end of the session. A skipped one isn't offered again.",
            "After a few days away the session shrinks to a welcome-back size. Whatever a session leaves over waits for another day; the \"review all\" link under the Start button takes the whole pile.",
            "\"Change\" next to the coast switches the focus coast.",
            "The basics come first: the first 120 new sentences come from the starter bank, translated by your provider. That is about six weeks at the Steady pace. After that your AI provider suggests sentences for your goal."
        )
    ),
    GuideSection(
        "Coasts and islands",
        listOfNotNull(
            "Each language you learn gets its own coast. Start one from My Coasts with \"New coast\".",
            "On a coast, your cards are grouped into islands by topic, like Travel or Restaurant.",
            "Every coast has a route through the levels A1, A2 and B1. The card at the top of a coast shows your level, how far you are and the next island to take; \"Route\" lists every island of every level.",
            "An island counts as you learn its cards, and a level is reached once 90% of its cards have been studied. The levels follow what A1, A2 and B1 cover; the app doesn't test you.",
            "A starter topic on the route (Greetings, Numbers, Weather, ...) is added as an island in one tap and translated while the app stays open. A level's \"Add … topics\" button adds all it has left, but a few topics at a time is the better pace.",
            "With an AI provider, a native-speaker check then rewrites anything in a starter topic that people wouldn't really say. The first version stays as an alternative and the note says why.",
            "Blanks like your name, city or job are filled in from About you, or asked for if you left them empty.",
            "The islands about you are the topics no bank can hold: your family, home, work, routine, hobbies, health, past and plans. Open About you from Settings, answer a topic's questions in a few words, and your AI provider writes up to 25 sentences about it, using only what you told it.",
            if (localAiAvailable) "Islands about you need an AI provider, cloud or local." else "Islands about you need an AI provider.",
            "\"Look them over\" afterwards lists what was written; untick anything that isn't true. Change an answer later and the island offers to be written again; cards you have already studied stay.",
            "Under About you, say whether sentences about you are worded as a woman, as a man or neutrally: many languages say \"I'm tired\" differently for each.",
            "At B1 your AI provider writes islands on what that level asks for (opinions with reasons, telling a story, complaints, what-ifs), and each island about you can be taken up a level.",
            "\"Rehearse as a talk\" in an island's ⋮ menu joins the island's sentences into about a minute of speech to read aloud, listen to, and then say without looking.",
            ("The PDF icon at the top of a coast makes a practice sheet: your AI provider writes new sentences to translate, each built from one of that coast's cards. " +
                "Islands whose cards you are forgetting get more of them, and an online AI provider explains words your cards don't have under the sentence. " +
                "Pick the islands, the direction, the difficulty and how many sentences; the answers come on a page of their own, each with the card it was built from. " +
                "Switch to Custom to say in your own words what to practise, such as \"the plural of nouns\", and get exercises on just that.")
                .takeIf { fileTransferAvailable }
        )
    ),
    GuideSection(
        "Creating cards",
        listOf(
            "On Today, open \"Add your own\", keep \"Translate\" picked, type what you want to say in your own language and tap \"Translate and save\".",
            "The card goes to the focus coast shown at the top of Today.",
            "Set your native language under Languages in Settings, so translations go the right way.",
            "Choose \"Correct mine\" to write in the language you're learning: the AI fills in your language and corrects slips. \"Type both\" saves exactly what you type, with no AI.",
            "\"Starter sentences\" is a bank of about 900 sentences and words everyone needs, in 37 topics: from greetings, small talk and getting around to renting, paperwork, numbers and the words a phone's settings use. Tick what you'd say, fill in blanks like [city] or [name], and they become cards.",
            if (runsOnAndroid) {
                "\"From text or a photo\" turns a pasted paragraph, or a photo of a page or a sign, into sentences to tick, one card each. " +
                    "Photos are read in languages written with Latin letters; for the others the button reads \"From text\"."
            } else {
                "\"From text\" turns a pasted paragraph into sentences to tick, one card each."
            },
            "Under \"Topic\", tap a chip or type a name to choose the island yourself. Leave it empty and your AI provider picks one. While you type, a likely topic is suggested.",
            "If the coast already has that sentence, a line under the text box says which island it is on.",
            "Made a mistake? Tap \"Undo\" right after saving."
        )
    ),
    GuideSection(
        "AI and translation providers",
        listOfNotNull(
            "On-device translation is free, private and works offline. It needs no setup, and is powered by Google Translate."
                .takeIf { onDeviceTranslationAvailable },
            ("Local AI is an AI model that runs on your phone, free and offline. Settings checks what your phone can run, suggests models that fit " +
                "and downloads the one you pick. One of them, Gemma 3, needs a free access code from Hugging Face.")
                .takeIf { localAiAvailable },
            "Online AI providers (Google Gemini, OpenAI, Anthropic Claude, Groq, DeepSeek and OpenRouter) translate with more feel for the language, sort cards onto islands, suggest alternatives and explain words.",
            "What is an API key? A long code you create in your own account with an online provider. Paste it under Translation in Settings; \"Get a key\" there opens the provider's page, and \"Walk me through it\" takes you through it step by step. Gemini is free to start; other providers bill you for what you use.",
            "Before anything goes to an online provider, the app says what it sends and asks you to allow it. Removing the key in Settings takes that back.",
            "Providers bring out new models and retire old ones. Under Translation in Settings, open \"Advanced: choose model\" to pick another one; \"Refresh model list\" there asks the provider what it offers now.",
            // While there is no address to mail to, the link opens the issue tracker, and the reader should know that page is public.
            if (Support.EMAIL.isBlank()) {
                "AI can make mistakes. \"Report this answer\" under an explanation or an answer opens the app's public issue tracker, " +
                    "where you can report one that is wrong or offensive. What you post there is public."
            } else {
                "AI can make mistakes. \"Report this answer\" under an explanation or an answer lets you tell the developer about one that is wrong or offensive."
            }
        )
    ),
    GuideSection(
        "Studying",
        listOf(
            "Today's session is the daily routine: the cards that are due, then a few new ones. Open a coast or an island from My Coasts to study just that. Spaced repetition decides when each card comes back.",
            "New cards don't pile up. An island you add or have written waits with its cards marked \"new\", and each day's session introduces a few of them.",
            "For a new card, \"Learn it\" lets you practice it at the end of the session, and \"I know this\" sends it days ahead.",
            "The pace sets how many new cards a day: Steady is 3, Fast 10, Sprint 20. Change it under the Start button on Today or in Settings. Opening an island studies all of it at once, if you'd rather.",
            "Flip: tap \"Show answer\", then grade yourself with Again, Hard, Good or Easy. " +
                "You can also swipe left for Again and right for Good.",
            "Type: type the translation and tap \"Check answer\". Capitals and punctuation don't matter, accents do. A slip of a letter counts as Hard.",
            "Fill gap: the translation has its key word blanked out. Type the missing word. Languages written without spaces, such as Japanese, have no Fill gap.",
            "Words you keep forgetting, on My Coasts, gathers the cards you keep forgetting into one short session.",
            "Tap the languages under the switch (e.g. \"English → German\") to study the other way round: you see the translation and answer in your own language.",
            "The ⋮ menu during a session has Previous card, Skip card and all of the island's cards.",
            "Nothing due? You can still practice all cards of the island."
        )
    ),
    GuideSection(
        "On the back of a card",
        listOf(
            "Tap the speaker to hear the translation read aloud. No speaker? The phone has no voice for that language yet; you can add one in the phone's speech settings.",
            "Tap any word to have your AI provider explain it, with a dict.cc link alongside. Languages written without spaces, such as Japanese, have no words to tap.",
            "If a card has alternatives, tap their count (\"2 alternatives\") to see other ways to say it, and \"Make main\" to swap one in."
        )
    ),
    GuideSection(
        "Streaks and reminders",
        listOfNotNull(
            "Finish a session each day to keep your streak going. One missed day a week is covered by a streak freeze.",
            "Pick a weekly island goal in Settings; My Coasts shows how far along you are. The recap icon there sums up your month.",
            "The daily reminder comes at a fixed time or a surprise time; set it under Study reminders in Settings. It can only appear once you allow the app to show notifications.",
            "A reminder can quote a card, which you can reveal and grade from the notification."
                .takeIf { runsOnAndroid },
            "Add the Language Coast widget to your home screen to see your streak and the cards due today."
                .takeIf { homeScreenWidgetAvailable }
        )
    ),
    GuideSection(
        "Your cards",
        listOfNotNull(
            // There is no account: the reader has to know before the phone is lost, not after.
            if (fileTransferAvailable) {
                "Your cards are saved on this phone only: there is no account and no copy online. To keep them safe, " +
                    "tap \"Back up\" under Backup in Settings now and then, and keep the file somewhere else, such as your e-mail or a cloud drive."
            } else {
                "Your cards are saved on this phone only: there is no account and no copy online, so removing the app removes them too."
            },
            "Tap the search icon on My Coasts to find a card on any coast by its native text, translation or alternatives.",
            "Tap ⋮ on an island and \"See cards\" to list its cards with when each is due next.",
            "Tap a card there, or \"Edit card\" in the ⋮ menu while studying, to fix its translation and alternatives or move it to another island.",
            "Not sure a card is right? \"Check translation\" in the ⋮ menu while studying has your AI provider read it as a native speaker would. Nothing changes unless you tap \"Use this\".",
            "Would you say it differently? In the same sheet, type your own translation under \"Correct as well?\". If your AI provider agrees, save it as an alternative: it then counts as correct when you type it.",
            "Something about a card you don't get? \"Ask about this card\" in the ⋮ menu while studying lets you ask your AI provider anything about it, and follow up on its answer.",
            "Export a coast to Anki, to a CSV file for a spreadsheet or to Markdown, or import cards from an Anki deck (.apkg) or a CSV file, under \"Your cards\" in Settings."
                .takeIf { fileTransferAvailable },
            "An imported Anki deck's notes become cards on islands named after its decks. If the deck has the foreign language on the front, turn on \"Swap sides\" in the preview."
                .takeIf { fileTransferAvailable },
            "Changing phones? \"Back up\" under Backup in Settings saves everything in one file, including your progress, your streak and your About you answers. \"Restore\" it on the new phone."
                .takeIf { fileTransferAvailable },
            "Deleted an island or a card by accident? Tap \"Undo\" in the message at the bottom. Deleting a whole coast, or an island with more than 10 cards, asks first."
        )
    )
)

/**
 * A bottom sheet explaining how to use the app, one short section per topic. The list is lazy, so opening
 * the sheet lays out its first screenful and not every tip, and it scrolls as a whole.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowToUseSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                Column {
                    Text("How to use Language Coast", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Build your coast, one word at a time.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
            GUIDE.forEach { section ->
                item {
                    Column {
                        HorizontalDivider(color = SurfaceRaised, modifier = Modifier.padding(vertical = 12.dp))
                        Text(section.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    }
                }
                items(section.tips) { tip ->
                    Row(modifier = Modifier.padding(top = 6.dp)) {
                        // The dot is decoration: a screen reader goes from tip to tip.
                        Text("•", color = TextSecondary, modifier = Modifier.clearAndSetSemantics {})
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(tip, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }
            }
        }
    }
}
