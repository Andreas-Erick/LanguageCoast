package com.andreaserick.languagecoast.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The translation prompt, pinned character for character. Custom sheets brought a second prompt and a sheet
 * kind beside this one; a translation sheet is to ask exactly what it asked before, so any change to these
 * texts has to be made here on purpose, never by the way.
 */
class WorksheetPromptGoldenTest {

    private val cards = listOf(
        WorksheetCard("Travel", "Wo ist der Bahnhof?", "Where is the station?"),
        WorksheetCard("Travel", "Ich gehe zum Strand.", "I am going to the beach."),
        WorksheetCard("Food", "Das Kind isst einen Apfel.", "The child is eating an apple."),
        WorksheetCard("Restaurant", "Die Rechnung, bitte.", "The bill, please.")
    )

    @Test
    fun aHardAnchoredPromptWithEveryOptionIsUnchanged() {
        val prompt = buildWorksheetPrompt(
            cards, "German", "English", count = 3, difficulty = WorksheetDifficulty.Hard, region = "Austria",
            avoid = listOf("Wo ist das Kind?", "Ich esse gern."),
            anchors = listOf(WorksheetAnchor(cards[3], note = "Polite [in cafés]"), WorksheetAnchor(cards[0]), WorksheetAnchor(cards[2])),
            level = Level.A2, wording = Wording.Feminine, direction = WorksheetDirection.ToTarget, wordHelp = true,
            goal = "Moving to Vienna\nfor work"
        )
        assertEquals(ANCHORED, prompt)
    }

    @Test
    fun aMediumPromptSpreadOverIslandsWithoutWordHelpIsUnchanged() {
        val prompt = buildWorksheetPrompt(
            cards, "German", "Swedish", count = 5, difficulty = WorksheetDifficulty.Medium,
            quotas = listOf(IslandQuota("Travel", 3), IslandQuota("Food", 2)),
            direction = WorksheetDirection.ToNative, wordHelp = false
        )
        assertEquals(SPREAD, prompt)
    }

    @Test
    fun anEasyPromptWithTheDefaultsIsUnchanged() {
        assertEquals(EASY, buildWorksheetPrompt(cards, "German", "English", count = 1, difficulty = WorksheetDifficulty.Easy))
    }

    private companion object {
        val ANCHORED = """You are a native German speaker from Austria who teaches German to adults. You are writing a translation exercise for one learner. Their native language is English and their level is about A2. Why they are learning German: "Moving to Vienna for work".

These are the learner's flashcards, grouped by topic, one per line, as "German = English":

[Travel]
Wo ist der Bahnhof? = Where is the station?
Ich gehe zum Strand. = I am going to the beach.

[Food]
Das Kind isst einen Apfel. = The child is eating an apple.

[Restaurant]
Die Rechnung, bitte. = The bill, please.

Write one new German sentence for each numbered line below, with its English translation. A line names a topic and one of the flashcards. The sentence must use that flashcard's key word or phrase, in whatever form the sentence needs, and say something new with it: something that really is said in that topic's situation. Give each sentence a second part that belongs to the first: a reason (because), a condition (if, when), a contrast (but, although) or what happens next. For each line pick the one that fits the flashcard best, and vary them over the exercise. Never bolt "because" onto a direct question, and when a flashcard is a short greeting, thanks or polite phrase, weave it into a natural situation (such as why the speaker is leaving, apologising or asking politely) rather than forcing an awkward clause onto it.

1. Restaurant: Die Rechnung, bitte. (Note: Polite (in cafés))
2. Travel: Wo ist der Bahnhof?
3. Food: Das Kind isst einen Apfel.

What matters, most important first:
1. The German sentence is correct and natural: what a native speaker from Austria would really say, with every ending, article, case, preposition and the word order right. Picture who says it, to whom and when. If a sentence only exists to reuse a word, write a different one.
2. The English line says the same thing the way a native speaker of English would say it: the same meaning, person, tense and politeness, nothing added and nothing dropped, and not word for word where that sounds odd.
3. It is a longer sentence of 9 to 16 words in two parts that belong together (a reason, a condition, a contrast or a second step), never two unrelated things joined. Its words and grammar suit level A2.
4. It leans on what the learner knows. Prefer words and patterns from the flashcards. Other words are welcome where the sentence needs them, as long as they are common, everyday words; list them after the translation.
5. No two sentences are the same or differ by a single word, and none repeats a flashcard.
- The learner is a woman: wherever German marks the speaker's gender (adjectives, participles, job titles), use the feminine forms for sentences she says about herself. This is a note on grammar; never say it in a sentence.
- Where the English line could fairly be translated in more than one way (formal or informal "you", one person or several), end it with a short hint in round brackets, such as (formal) or (to a friend).
- Build the second part from a realistic everyday situation and, only where they genuinely fit together, words from the other flashcards above; never stitch two unrelated flashcards into a contrived scene just to reuse vocabulary. Introduce at most two or three new German content words per sentence so the learner is not overwhelmed, and list every new German word in the last field.
- Do not repeat any of these, which are already in the exercise: Wo ist das Kind? | Ich esse gern.

Answer with exactly one line per number and nothing else, in this format:
[number] || [German sentence] || [English translation] || [the German words that are not on the flashcards, each with its English meaning, as "word = meaning; word = meaning", or - if there are none]"""
        val SPREAD = """You are a native German speaker who teaches German to adults. You are writing a translation exercise for one learner. Their native language is Swedish.

These are the learner's flashcards, grouped by topic, one per line, as "German = Swedish":

[Travel]
Wo ist der Bahnhof? = Where is the station?
Ich gehe zum Strand. = I am going to the beach.

[Food]
Das Kind isst einen Apfel. = The child is eating an apple.

[Restaurant]
Die Rechnung, bitte. = The bill, please.

Write 5 new German sentences for the learner to translate, each with its Swedish translation. Each sentence takes a word or phrase from one topic's flashcards and says something new with it: something that really is said in that topic's situation. Of the sentences, write 3 about Travel and 2 about Food. The other topics get no sentence of their own this time. Do something different with the word or phrase each time: ask a question with it, say what is not the case, say it about someone else (he, she, we or they), or add a time or a place. For each sentence pick the change that gives the most natural sentence, and vary the changes over the exercise.

What matters, most important first:
1. The German sentence is correct and natural: what a native speaker would really say, with every ending, article, case, preposition and the word order right. Picture who says it, to whom and when. If a sentence only exists to reuse a word, write a different one.
2. The Swedish line says the same thing the way a native speaker of Swedish would say it: the same meaning, person, tense and politeness, nothing added and nothing dropped, and not word for word where that sounds odd.
3. It is an everyday sentence of 5 to 10 words. Its words and grammar suit the learner, whose level the flashcards show.
4. It leans on what the learner knows. Prefer words and patterns from the flashcards. Other words are welcome where the sentence needs them, as long as they are common, everyday words.
5. No two sentences are the same or differ by a single word, and none repeats a flashcard.

Answer with exactly one line per sentence and nothing else, in this format:
[German sentence] || [Swedish translation]"""
        val EASY = """You are a native German speaker who teaches German to adults. You are writing a translation exercise for one learner. Their native language is English.

These are the learner's flashcards, grouped by topic, one per line, as "German = English":

[Travel]
Wo ist der Bahnhof? = Where is the station?
Ich gehe zum Strand. = I am going to the beach.

[Food]
Das Kind isst einen Apfel. = The child is eating an apple.

[Restaurant]
Die Rechnung, bitte. = The bill, please.

Write 1 new German sentence for the learner to translate, each with its English translation. Each sentence takes a word or phrase from one topic's flashcards and says something new with it: something that really is said in that topic's situation. Spread the sentences over the topics.

What matters, most important first:
1. The German sentence is correct and natural: what a native speaker would really say, with every ending, article, case, preposition and the word order right. Picture who says it, to whom and when. If a sentence only exists to reuse a word, write a different one.
2. The English line says the same thing the way a native speaker of English would say it: the same meaning, person, tense and politeness, nothing added and nothing dropped, and not word for word where that sounds odd.
3. It is short and simple: 3 to 7 words in one clause. Its words and grammar suit the learner, whose level the flashcards show.
4. It leans on what the learner knows. Prefer words and patterns from the flashcards. Other words are welcome where the sentence needs them, as long as they are common, everyday words; list them after the translation.
5. No two sentences are the same or differ by a single word, and none repeats a flashcard.
- Where the English line could fairly be translated in more than one way (formal or informal "you", one person or several), end it with a short hint in round brackets, such as (formal) or (to a friend).

Answer with exactly one line per sentence and nothing else, in this format:
[German sentence] || [English translation] || [the German words that are not on the flashcards, each with its English meaning, as "word = meaning; word = meaning", or - if there are none]"""
    }
}
