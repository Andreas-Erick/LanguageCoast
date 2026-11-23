package com.andreaserick.languagecoast.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The chips of the languages beyond German and the Nordic ones: each offers only grammar it has. */
class WorldBriefChipsTest {

    private fun chipsOf(language: String) = briefChips(language).associate { it.area to it.chips }

    private fun allChipsOf(language: String) = briefChips(language).flatMap { it.chips }

    private val worldLanguages = listOf(
        "Albanian", "Bosnian", "Bulgarian", "Croatian", "Czech", "Dutch", "English", "Esperanto", "French",
        "Greek", "Hungarian", "Italian", "Japanese", "Korean", "Latin", "Polish", "Portuguese", "Romanian",
        "Russian", "Serbian", "Slovak", "Spanish", "Turkish", "Ukrainian"
    )

    @Test
    fun everyOneOfTheTwentyFourLanguagesHasAProfileOfItsOwn() {
        worldLanguages.forEach { name ->
            assertTrue(name in GrammarProfiles.BY_LANGUAGE, "$name has no profile")
            assertFalse(name in GrammarProfiles.GENERAL_ON_PURPOSE, "$name still falls back to the general chips")
            val chips = allChipsOf(name)
            // A short list a learner can scan, with no chip twice and none too long for a chip.
            assertEquals(chips.distinct(), chips, "$name repeats a chip")
            chips.forEach { assertTrue(it.length <= 60, "$name: \"$it\" is too long for a chip") }
        }
    }

    @Test
    fun polishHasSevenCasesAndAspect() {
        val polish = chipsOf("Polish")
        assertEquals(7, GrammarProfiles.of("Polish")!!.cases.size)
        assertEquals(
            listOf("genitive", "dative", "accusative", "instrumental", "locative", "vocative").map { "Nominative → $it" },
            polish[ChipArea.Cases]!!.take(6)
        )
        assertTrue("Perfective or imperfective verb" in polish[ChipArea.Verbs]!!)
        // Polish has no articles, so its genders get a drill of their own.
        assertNull(polish[ChipArea.Articles])
        assertTrue("Gender of nouns" in polish[ChipArea.Nouns]!!)
    }

    @Test
    fun everySlavicLanguageHasAspectAndOnlyBulgarianLacksCases() {
        listOf("Bosnian", "Croatian", "Czech", "Polish", "Russian", "Serbian", "Slovak", "Ukrainian").forEach { name ->
            assertTrue("Perfective or imperfective verb" in allChipsOf(name), "$name has no aspect chip")
            assertTrue(chipsOf(name)[ChipArea.Cases].orEmpty().size >= 5, "$name has too few case chips")
            assertNull(chipsOf(name)[ChipArea.Articles], "$name has no articles")
        }
        // Russian has six cases, the prepositional among them and no vocative.
        assertEquals(
            listOf("genitive", "dative", "accusative", "instrumental", "prepositional").map { "Nominative → $it" },
            chipsOf("Russian")[ChipArea.Cases]!!.take(5)
        )
        assertEquals(6, GrammarProfiles.of("Russian")!!.cases.size)
    }

    @Test
    fun bulgarianHasASuffixedArticleAndNoCases() {
        val bulgarian = chipsOf("Bulgarian")
        assertNull(bulgarian[ChipArea.Cases])
        assertEquals("Noun in its definite form", bulgarian[ChipArea.Articles]!!.first())
        assertTrue("Perfective or imperfective verb" in bulgarian[ChipArea.Verbs]!!)
        assertEquals(listOf("Personal pronouns", "Possessive pronouns", "Short object pronouns (го, ѝ, му)"), bulgarian[ChipArea.Pronouns])
    }

    @Test
    fun romanianAndAlbanianHaveSuffixedArticlesAndCases() {
        listOf("Romanian", "Albanian").forEach { name ->
            val chips = chipsOf(name)
            assertEquals("Noun in its definite form", chips[ChipArea.Articles]!!.first(), name)
            assertTrue(chips[ChipArea.Cases]!!.isNotEmpty(), "$name has cases")
            // The definite article is part of the noun, so there is no "Definite article in every case" chip.
            assertFalse("Definite article in every case" in chips[ChipArea.Articles]!!, name)
        }
        assertEquals(listOf("Nominative → genitive-dative"), chipsOf("Romanian")[ChipArea.Cases]!!.take(1))
    }

    @Test
    fun greekHasCasesAndArticlesThatDecline() {
        val greek = chipsOf("Greek")
        assertEquals(listOf("Nominative → genitive", "Nominative → accusative"), greek[ChipArea.Cases]!!.take(2))
        assertTrue("Definite article in every case" in greek[ChipArea.Articles]!!)
        assertTrue("Perfective or imperfective verb" in greek[ChipArea.Verbs]!!)
    }

    @Test
    fun hungarianHasCasesButNoGender() {
        val hungarian = chipsOf("Hungarian")
        assertTrue(GrammarProfiles.of("Hungarian")!!.genders.isEmpty())
        assertFalse(allChipsOf("Hungarian").any { "gender" in it.lowercase() })
        assertTrue("Nominative → accusative" in hungarian[ChipArea.Cases]!!)
        // Hungarian has postpositions, not prepositions.
        assertFalse(allChipsOf("Hungarian").any { it.startsWith("Prepositions") })
        assertTrue(hungarian[ChipArea.Prepositions]!!.single().startsWith("Postpositions"))
        assertTrue(hungarian[ChipArea.Verbs]!!.any { it.startsWith("Definite or indefinite conjugation") })
    }

    @Test
    fun turkishHasCasesAndVowelHarmonyButNoGenderOrArticles() {
        val turkish = chipsOf("Turkish")
        assertNull(turkish[ChipArea.Articles])
        assertFalse(allChipsOf("Turkish").any { "gender" in it.lowercase() })
        assertEquals(
            listOf("accusative", "dative", "locative", "ablative", "genitive").map { "Nominative → $it" },
            turkish[ChipArea.Cases]!!.take(5)
        )
        assertTrue(turkish[ChipArea.Nouns]!!.any { it.startsWith("Vowel harmony") })
        assertTrue(turkish[ChipArea.Prepositions]!!.single().startsWith("Postpositions"))
    }

    @Test
    fun japaneseAndKoreanHaveParticlesAndPolitenessButNoCasesArticlesOrPrepositions() {
        listOf("Japanese", "Korean").forEach { name ->
            val chips = chipsOf(name)
            assertNull(chips[ChipArea.Cases], name)
            assertNull(chips[ChipArea.Articles], name)
            assertNull(chips[ChipArea.Prepositions], name)
            assertTrue("Particles" in chips[ChipArea.Particles]!!, name)
            assertTrue("Polite and plain forms" in chips[ChipArea.Politeness]!!, name)
            // Nouns don't take a plural ending, and possession is a particle, not a pronoun of its own.
            assertFalse("Plural from the singular" in allChipsOf(name), name)
            assertEquals(listOf("Personal pronouns"), chips[ChipArea.Pronouns], name)
        }
        assertTrue(chipsOf("Japanese")[ChipArea.Particles]!!.any { "は" in it && "が" in it })
    }

    @Test
    fun spanishHasSerOrEstarAndPreteriteOrImperfect() {
        val spanish = chipsOf("Spanish")
        assertTrue("ser or estar" in spanish[ChipArea.Verbs]!!)
        assertTrue("Preterite or imperfect" in spanish[ChipArea.Tenses]!!)
        assertTrue("por or para" in spanish[ChipArea.Prepositions]!!)
        assertNull(spanish[ChipArea.Cases])
        assertEquals(listOf("Noun with its definite article", "Noun with its indefinite article"), spanish[ChipArea.Articles])
    }

    @Test
    fun esperantoDrillsItsAccusativeEnding() {
        val esperanto = chipsOf("Esperanto")
        assertEquals("Nominative → accusative", esperanto[ChipArea.Cases]!!.first())
        assertTrue(esperanto[ChipArea.Cases]!!.any { "-n" in it })
        // La is its only article and never changes, and nouns have no gender.
        assertNull(esperanto[ChipArea.Articles])
        assertFalse(allChipsOf("Esperanto").any { "gender" in it.lowercase() })
    }

    @Test
    fun latinHasItsCasesAndNoArticles() {
        val latin = chipsOf("Latin")
        assertEquals(
            listOf("genitive", "dative", "accusative", "ablative", "vocative").map { "Nominative → $it" },
            latin[ChipArea.Cases]!!.take(5)
        )
        assertNull(latin[ChipArea.Articles])
        assertTrue("Gender of nouns" in latin[ChipArea.Nouns]!!)
    }

    @Test
    fun dutchHasDeOrHetSeparableVerbsAndVerbSecond() {
        val dutch = chipsOf("Dutch")
        assertTrue("de or het" in dutch[ChipArea.Articles]!!)
        assertTrue("Separable verbs" in dutch[ChipArea.Verbs]!!)
        assertTrue("Verb second (V2) and inversion" in dutch[ChipArea.WordOrder]!!)
        assertNull(dutch[ChipArea.Cases])
    }

    @Test
    fun aLanguageWithoutPrepositionsOrPossessivePronounsGetsNoChipsForThem() {
        val chips = briefChips(GrammarProfile(prepositions = false, possessivePronouns = false)).associate { it.area to it.chips }
        assertNull(chips[ChipArea.Prepositions])
        assertEquals(listOf("Personal pronouns"), chips[ChipArea.Pronouns])
    }
}
