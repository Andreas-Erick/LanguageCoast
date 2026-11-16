package com.andreaserick.languagecoast.ui.coast

import com.andreaserick.languagecoast.data.TranslationProvider
import com.andreaserick.languagecoast.data.WorksheetBatch
import com.andreaserick.languagecoast.data.worksheetAsk
import com.andreaserick.languagecoast.data.worksheetRequests
import kotlin.test.Test
import kotlin.test.assertEquals

class WorksheetEstimateTest {

    @Test
    fun aSheetSaysHowLongItsProviderTakesOverIt() {
        // A cloud model answers a sheet's sentences in seconds.
        assertEquals("Gemini writes the sentences. It takes less than a minute.", worksheetEstimate(10, TranslationProvider.Gemini))
        // Claude's models think before they answer: a dozen sentences take a minute or two, and a longer sheet several requests.
        assertEquals("Claude writes the sentences. It takes about 2 minutes.", worksheetEstimate(10, TranslationProvider.Claude))
        assertEquals("Claude writes the sentences a few at a time, in 3 requests. It takes about 5 minutes.", worksheetEstimate(30, TranslationProvider.Claude))
        assertEquals("Local AI writes the sentences a few at a time, in 2 requests. It takes about a minute.", worksheetEstimate(10, TranslationProvider.LocalAi))
    }

    @Test
    fun theLastFewSentencesGoWithTheRequestBeforeThem() {
        // A dozen at a time, and up to three more rather than a request of their own.
        assertEquals(12, worksheetAsk(left = 40, batch = 12))
        assertEquals(15, worksheetAsk(left = 15, batch = 12))
        assertEquals(12, worksheetAsk(left = 16, batch = 12))
        assertEquals(4, worksheetAsk(left = 4, batch = 12))

        // The number of requests a sheet says it takes is the number it makes.
        for (batch in WorksheetBatch.entries) for (count in 3..120) {
            var left = count + if (batch.hasSpares) (count / 4).coerceIn(2, 10) else 0
            var requests = 0
            while (left > 0) {
                left -= worksheetAsk(left, batch.sentences)
                requests++
            }
            assertEquals(requests, worksheetRequests(count, batch), "$batch, $count sentences")
        }
    }
}
