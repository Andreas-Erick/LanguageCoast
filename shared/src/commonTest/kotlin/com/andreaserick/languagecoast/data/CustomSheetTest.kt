package com.andreaserick.languagecoast.data

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CustomSheetTest {

    private fun customSheet(exercises: List<Exercise>, brief: String = "Plural from the singular") = Worksheet(
        coastName = "German Coast",
        targetLanguage = "German",
        nativeLanguage = "English",
        options = WorksheetOptions(kind = SheetKind.Custom, brief = brief),
        sentences = emptyList(),
        date = LocalDate(2026, 11, 5),
        providerLabel = "Gemini",
        cardCount = 6,
        islandCount = 2,
        exercises = exercises
    )

    private fun short(task: String, answer: String, instruction: String? = "Write the plural.") =
        Exercise(ExerciseForm.ShortAnswer, instruction, task, answer)

    @Test
    fun aSheetIsATranslationSheetUnlessTheLearnerPicksCustom() {
        val options = WorksheetOptions()
        assertEquals(SheetKind.Translation, options.kind)
        assertEquals("", options.brief)
        assertEquals(emptySet(), options.forms)
    }

    @Test
    fun aCustomSheetsFileSaysItIsACustomSheet() {
        val sheet = customSheet(listOf(short("der Hund", "die Hunde")))
        assertEquals("german-coast-custom-practice-2026-11-05.pdf", sheet.fileName)
        assertEquals(1, sheet.exerciseCount)
        // Exercises come without cards, so the first page names no islands.
        assertNull(sheet.focusLine)
    }
}
