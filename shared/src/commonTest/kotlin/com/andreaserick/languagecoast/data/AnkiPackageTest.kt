package com.andreaserick.languagecoast.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AnkiPackageTest {

    @Test
    fun aNotesFieldsAreSplitWhereAnkiSeparatesThem() {
        assertEquals(AnkiNote(listOf("Front", "Back, with a comma", ""), "German::Greetings"), ankiNote("Front\u001FBack, with a comma\u001F", "German::Greetings"))
        assertEquals(AnkiNote(listOf("alone")), ankiNote("alone", null))
    }

    @Test
    fun theOlderFormatsDeckNamesAreReadFromItsJson() {
        val json = """{"1": {"id": 1, "name": "Default", "usn": 0}, "1700000000001": {"name": "German::Greetings", "id": 1700000000001, "lrnToday": [0, 0]}}"""

        assertEquals(mapOf(1L to "Default", 1700000000001L to "German::Greetings"), ankiDeckNames(json))
        // The newer format leaves the column empty; a damaged one is no reason to lose the notes.
        assertEquals(emptyMap(), ankiDeckNames(""))
        assertEquals(emptyMap(), ankiDeckNames("[1, 2]"))
        assertEquals(emptyMap(), ankiDeckNames("{not json"))
        assertEquals(mapOf(2L to "Kept"), ankiDeckNames("""{"x": {"name": "No number"}, "1": {"id": 1}, "2": {"name": "Kept"}, "3": 7}"""))
    }

    @Test
    fun aFieldsTextIsItsHtmlWithoutTagsSoundsAndPictures() {
        assertEquals("Car", ankiFieldText("<b>Car</b>"))
        assertEquals("Back for Car", ankiFieldText("Back for Car<br><img src=\"download.jpg\"><br>[sound:cable-car.mp3]"))
        assertEquals("", ankiFieldText("[sound:foo.wav]"))
        assertEquals("", ankiFieldText("<div><img src=\"only a picture.png\"></div>"))
        assertEquals("Thank you very much", ankiFieldText("<span style=\"color: red\">Thank  you</span> very&nbsp;much "))
        assertEquals("a < b & c > d \"e\" 'f'", ankiFieldText("a &lt; b &amp; c &gt; d &quot;e&quot; &#39;f&#39;"))
        assertEquals("Käse, Tür, &#0; &bogus;", ankiFieldText("K&#228;se, T&#xFC;r, &#0; &bogus;"))
        assertEquals("visible", ankiFieldText("<style>.card { color: red; }</style>visible<script>alert(1)</script>"))
    }

    @Test
    fun blocksAndBreaksStayAsLines() {
        assertEquals("one\ntwo\nthree", ankiFieldText("one<div>two</div><div><br></div><p>three</p>"))
        assertEquals("a\nb\nc", ankiFieldText("a<br>b<BR />c<br/>"))
        assertEquals("first\nsecond", ankiFieldText("<ul><li>first</li><li> second </li></ul>"))
    }

    @Test
    fun clozeDeletionsAreFilledIn() {
        assertEquals("The cheese is good.", ankiFieldText("The {{c1::cheese::food}} is good."))
        assertEquals("Der Hund und die Katze", ankiFieldText("Der {{c1::Hund}} und die {{c2::<b>Katze</b>}}"))
    }

    @Test
    fun aNoteBecomesACardFromItsFirstTwoFieldsWithText() {
        val notes = listOf(
            ankiNote("Good morning\u001FGuten Morgen", "German::Greetings"),
            // A sound-only field between the two sides, and more fields after them.
            ankiNote("bread\u001F[sound:brot.mp3]\u001Fdas Brot\u001FIch esse Brot.", "German\u001FFood & Drink"),
            ankiNote("<div>two</div><div>lines</div>\u001Fzwei<br>Zeilen", null),
            ankiNote("only one side\u001F", "German::Greetings"),
            ankiNote("\u001F<img src=\"x.png\">\u001F", "German::Greetings"),
            // What a package holds in place of its notes when it needs a newer reader than the one opening it.
            ankiNote("Please update to the latest Anki version, then import the .colpkg/.apkg file again.\u001F", "Default")
        )

        assertEquals(
            listOf(
                ImportedCard("Good morning", "Guten Morgen", "Greetings"),
                ImportedCard("bread", "das Brot", "Food & Drink"),
                ImportedCard("two lines", "zwei Zeilen", null)
            ),
            ankiNoteCards(notes)
        )
    }

    @Test
    fun theIslandIsTheLastLevelOfTheDecksName() {
        assertEquals("Greetings", ankiIsland("German::Greetings"))
        assertEquals("Year 1", ankiIsland("Spanish\u001FA-Level Vocabulary\u001FYear 1"))
        assertEquals("250 Most common spanish words", ankiIsland("250 Most common spanish words"))
        // The deck for cards that are filed nowhere isn't a topic.
        assertNull(ankiIsland("Default"))
        assertNull(ankiIsland("German::"))
        assertEquals("Verbs", ankiIsland("Default::Verbs"))
    }

    @Test
    fun aCardThisAppExportedToAnkiComesBackWithItsAlternativesAndNote() {
        val note = ankiNote(
            "Do you want to come?\u001FMöchtest du kommen?<br><br>Also: Willst du kommen? / Möchten Sie kommen?<br>Note: Sie is formal.",
            "German Coast::Small Talk"
        )

        assertEquals(
            listOf(
                ImportedCard(
                    "Do you want to come?", "Möchtest du kommen?", "Small Talk",
                    alternatives = listOf("Willst du kommen?", "Möchten Sie kommen?"), note = "Sie is formal."
                )
            ),
            ankiNoteCards(listOf(note))
        )
    }
}
