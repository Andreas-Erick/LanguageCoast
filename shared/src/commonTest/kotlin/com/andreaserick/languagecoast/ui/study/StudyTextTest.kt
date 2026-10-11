package com.andreaserick.languagecoast.ui.study

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
}
