package com.andreaserick.languagecoast.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PersonalIslandsTest {

    private val family = PersonalIslands.byId("family")!!
    private val work = PersonalIslands.byId("work")!!

    @Test
    fun islandsAndQuestionsAreToldApart() {
        val islands = PersonalIslands.ALL
        assertEquals(islands.size, islands.distinctBy { it.id }.size)
        assertEquals(islands.size, islands.distinctBy { it.name }.size)
        assertEquals(PersonalIslands.QUESTIONS.size, PersonalIslands.QUESTIONS.distinctBy { it.key }.size)
        assertTrue(islands.all { it.questions.size >= 2 })
        // An island about the user must not land on a starter topic's island.
        assertTrue(islands.none { PhraseBank.theme(it.name) != null })
        assertTrue(islands.flatMap { it.profileFields }.all { it in Profile.PLACEHOLDERS })
    }

    @Test
    fun anIslandOpensWithItsFirstAnswerAndListsWhatIsKnown() {
        assertFalse(work.isUnlocked(emptyMap()))
        assertFalse(work.isUnlocked(mapOf("work_place" to "  ", "family_parents" to "Maria and Paolo")))
        val answers = mapOf("work_place" to " at a hospital ")
        assertTrue(work.isUnlocked(answers))

        val facts = work.facts(Profile(name = "Anna", job = "nurse"), answers)

        // The job is a fact for this island; the name is not.
        assertEquals(listOf("Your job" to "nurse", "Where do you work or study?" to "at a hospital"), facts)
    }

    @Test
    fun theFingerprintChangesWithTheFactsOfThatIslandOnly() {
        val profile = Profile(name = "Anna", job = "nurse")
        val answers = mapOf("work_place" to "at a hospital")
        val before = work.fingerprint(profile, answers)

        assertEquals(before, work.fingerprint(profile.copy(name = "Anne"), answers + ("family_parents" to "Maria")))
        assertNotEquals(before, work.fingerprint(profile.copy(job = "doctor"), answers))
        assertNotEquals(before, work.fingerprint(profile, answers + ("work_hours" to "8 to 5")))
    }

    @Test
    fun thePromptCarriesTheFactsTheLevelAndTheLimit() {
        val prompt = buildPersonalIslandPrompt(
            family, listOf("Brothers and sisters: names, ages, what they do" to "one sister, Giulia,\n38"),
            nativeLanguage = "English", targetLanguage = "Spanish", region = "Mexico", goal = "Moving to Oaxaca",
            count = 25, avoid = listOf("I have one sister.")
        )

        assertTrue("\"My Family\"" in prompt)
        assertTrue("- Brothers and sisters: names, ages, what they do: one sister, Giulia, 38" in prompt)
        assertTrue("Spanish as spoken in Mexico" in prompt)
        assertTrue("register of Mexico" in prompt)
        assertTrue("Moving to Oaxaca" in prompt)
        assertTrue("Level A2" in prompt)
        assertTrue("Write up to 25 sentences" in prompt)
        assertTrue("Do not invent" in prompt)
        assertTrue("write fewer" in prompt)
        assertTrue("I have one sister." in prompt)
        assertTrue("[number] || [the sentence in English] || [the sentence in Spanish]" in prompt)
    }

    @Test
    fun thePromptLeavesOutWhatWasNotGiven() {
        val prompt = buildPersonalIslandPrompt(family, listOf("Other relatives you're close to" to "Rosa"), "English", "German", null, null, 25)

        assertFalse("They will use this in" in prompt)
        assertFalse("Why they are learning" in prompt)
        assertFalse("already have" in prompt)
    }

    @Test
    fun theReplyBecomesSentencesOnTheIslandWithoutRepeats() {
        val reply = """
            1 || I have one sister. || Tengo una hermana.
            2 || Her name is Giulia. || Se llama Giulia.
            3 || I have one sister! || Tengo una hermana.
            4 || My sister is a doctor.
            5 || "Do you have brothers or sisters?" || "¿Tienes hermanos?"
        """.trimIndent()

        val sentences = parsePersonalIslandResponse(reply, family, count = 25, avoid = listOf("Her name is Giulia"))

        assertEquals(listOf("I have one sister.", "Do you have brothers or sisters?"), sentences.map { it.nativeText })
        assertEquals("Tengo una hermana.", sentences.first().targetText)
        assertTrue(sentences.all { it.category == "My Family" && it.emoji == "👪" })
    }
}
