package com.andreaserick.languagecoast.ui.study

import androidx.compose.ui.unit.LayoutDirection
import com.andreaserick.languagecoast.data.splitPunctuation
import com.andreaserick.languagecoast.data.lookupWords
import com.andreaserick.languagecoast.data.Grade
import kotlin.test.Test
import kotlin.test.assertEquals

class StudyTextTest {

    @Test
    fun intervalLabelsAreShort() {
        assertEquals("1d", intervalLabel(1))
        assertEquals("29d", intervalLabel(29))
        assertEquals("2mo", intervalLabel(61))
        assertEquals("1y", intervalLabel(365))
        assertEquals("1.5y", intervalLabel(548))
    }

    @Test
    fun punctuationIsSplitFromWords() {
        assertEquals(Triple("", "hestur", ","), splitPunctuation("hestur,"))
        assertEquals(Triple("¿", "Dónde", ""), splitPunctuation("¿Dónde"))
        assertEquals(Triple("\"", "it's", "\"!"), splitPunctuation("\"it's\"!"))
        assertEquals(Triple("—", "", ""), splitPunctuation("—"))
    }

    @Test
    fun theWordsACardOffersToExplainAreItsLookupWordsEachOnce() {
        assertEquals(listOf("Dónde", "está", "la", "playa"), lookupWords("¿Dónde está la playa?"))
        // A list's numbers are no words, a word said twice is offered once, and lines count like spaces.
        assertEquals(listOf("das", "Haus", "Heim"), lookupWords("1. das Haus\n2. das Heim"))
        assertEquals(emptyList(), lookupWords("  — 12 "))
    }

    @Test
    fun intervalsAreSpelledOutForScreenReaders() {
        assertEquals("1 day", spokenInterval(1))
        assertEquals("29 days", spokenInterval(29))
        assertEquals("1 month", spokenInterval(30))
        assertEquals("2 months", spokenInterval(61))
        assertEquals("1 year", spokenInterval(365))
        assertEquals("1.5 years", spokenInterval(548))
        assertEquals("2 years", spokenInterval(730))
    }

    @Test
    fun gradeButtonsAreDescribedInWords() {
        assertEquals("Again, back later in this session", gradeDescription(Grade.Again, null))
        assertEquals("Good, back in 3 days", gradeDescription(Grade.Good, 3))
        assertEquals("Easy, back in 1 month", gradeDescription(Grade.Easy, 30))
        assertEquals("Hard", gradeDescription(Grade.Hard, null))
    }

    @Test
    fun aSentenceIsLaidOutInTheDirectionOfItsScript() {
        assertEquals(LayoutDirection.Ltr, sentenceDirection("¿Dónde está la playa?"))
        assertEquals(LayoutDirection.Ltr, sentenceDirection("123"))
        assertEquals(LayoutDirection.Rtl, sentenceDirection("שלום עולם"))
        assertEquals(LayoutDirection.Rtl, sentenceDirection("\"مرحبا\""))
    }
}
