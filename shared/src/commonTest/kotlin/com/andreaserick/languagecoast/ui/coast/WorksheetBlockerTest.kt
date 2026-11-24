package com.andreaserick.languagecoast.ui.coast

import com.andreaserick.languagecoast.data.SheetKind
import com.andreaserick.languagecoast.data.TranslationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorksheetBlockerTest {

    private fun blocker(
        picked: Int = 2,
        allPicked: Boolean = true,
        cards: Int = 20,
        provider: TranslationProvider = TranslationProvider.Gemini,
        kind: SheetKind = SheetKind.Custom,
        brief: String = "Plural from the singular"
    ) = worksheetBlocker(picked, allPicked, cards, provider, kind, brief)

    @Test
    fun aCustomSheetWithoutABriefSaysWhatToDoInsteadOfLeavingTheButtonGreyedOut() {
        assertEquals("Name what to practise above, or tap an example.", blocker(brief = ""))
        assertEquals("Name what to practise above, or tap an example.", blocker(brief = "  \n "))
        // A translation sheet has no brief to name.
        assertNull(blocker(kind = SheetKind.Translation, brief = ""))
        assertNull(blocker())
    }

    @Test
    fun whatCantBeChangedHereIsSaidBeforeTheMissingBrief() {
        // Islands, cards and the provider come first: naming a brief wouldn't help with those.
        assertEquals("Pick at least one island to make the sheet from.", blocker(picked = 0, allPicked = false, cards = 0, brief = ""))
        assertEquals(
            "On-device translation can't write exercises. Pick an AI provider in Settings to make a practice sheet.",
            blocker(provider = TranslationProvider.OnDevice, brief = "")
        )
        assertEquals(
            "A practice sheet is made from the coast's cards, and this coast has 3 cards. Add at least 5 first.",
            blocker(cards = 3, brief = "")
        )
    }
}
