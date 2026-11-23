package com.andreaserick.languagecoast.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Nordic languages' chip sets: the maintainer's focus languages, so each is spelled out in full. */
class NordicBriefChipsTest {

    private fun chipsOf(language: String) = briefChips(language).associate { it.area to it.chips }

    @Test
    fun swedishShowsItsFullChipSetAreaByArea() {
        assertEquals(
            mapOf(
                ChipArea.Articles to listOf(
                    "en or ett: en bil, ett hus",
                    "Definite ending: bil → bilen",
                    "Definite plural: bilar → bilarna",
                    "Double definiteness: den stora bilen"
                ),
                ChipArea.Nouns to listOf("Plural from the singular", "Plural groups: flickor, bilar, parker, äpplen, hus"),
                ChipArea.Adjectives to listOf("Adjective agreement: stor, stort, stora", "Comparative and superlative: stor, större, störst"),
                ChipArea.Verbs to listOf(
                    "Verb groups: past and supine (tala → talade, har talat)",
                    "Strong verbs: skriva → skrev, har skrivit",
                    "s-passive: boken läses"
                ),
                ChipArea.Tenses to listOf("Past tense", "Perfect tense", "Pluperfect tense", "Future with ska or kommer att"),
                ChipArea.Pronouns to listOf("Personal pronouns", "Possessive pronouns", "sin or hans: Han tog sin bil"),
                ChipArea.Prepositions to listOf("Prepositions"),
                ChipArea.WordOrder to listOf("V2: inversion after an adverb (I dag åker jag)", "Subordinate clauses: att han inte kommer")
            ),
            chipsOf("Swedish")
        )
    }

    @Test
    fun norwegianShowsItsFullChipSetInBokmal() {
        assertEquals(
            mapOf(
                ChipArea.Articles to listOf(
                    "en, ei or et: en bil, ei jente, et hus",
                    "Definite ending: bil → bilen, jente → jenta",
                    "Definite plural: biler → bilene",
                    "Double definiteness: den store bilen"
                ),
                ChipArea.Nouns to listOf("Plural from the singular", "Plural groups: biler, hus, menn"),
                ChipArea.Adjectives to listOf("Adjective agreement: stor, stort, store", "Comparative and superlative: stor, større, størst"),
                ChipArea.Verbs to listOf(
                    "Verb groups: past and perfect (kaste → kastet, har kastet)",
                    "Strong verbs: skrive → skrev, har skrevet",
                    "s-passive: boken leses"
                ),
                ChipArea.Tenses to listOf("Past tense", "Perfect tense", "Pluperfect tense", "Future with skal or kommer til å"),
                ChipArea.Pronouns to listOf(
                    "Personal pronouns",
                    "Possessive pronouns",
                    "sin or hans: Han tok bilen sin",
                    "Possessive after the noun: bilen min"
                ),
                ChipArea.Prepositions to listOf("Prepositions"),
                ChipArea.WordOrder to listOf("V2: inversion after an adverb (I dag reiser jeg)", "Subordinate clauses: at han ikke kommer")
            ),
            chipsOf("Norwegian")
        )
    }

    @Test
    fun danishShowsItsFullChipSetWithoutDoubleDefiniteness() {
        assertEquals(
            mapOf(
                ChipArea.Articles to listOf(
                    "en or et: en bil, et hus",
                    "Definite ending: bil → bilen",
                    "Definite plural: biler → bilerne",
                    // Danish, unlike Swedish and Norwegian, drops the ending after an adjective.
                    "Definite with an adjective: den store bil"
                ),
                ChipArea.Nouns to listOf("Plural from the singular", "Plural groups: biler, huse, år"),
                ChipArea.Adjectives to listOf("Adjective agreement: stor, stort, store", "Comparative and superlative: stor, større, størst"),
                ChipArea.Verbs to listOf(
                    "Verb groups: past and perfect (lave → lavede, har lavet)",
                    "Strong verbs: skrive → skrev, har skrevet",
                    "s-passive: bogen læses"
                ),
                ChipArea.Tenses to listOf("Past tense", "Perfect tense", "Pluperfect tense", "Perfect with have or være: har spist, er gået"),
                ChipArea.Pronouns to listOf("Personal pronouns", "Possessive pronouns", "sin or hans: Han tog sin bil"),
                ChipArea.Prepositions to listOf("Prepositions"),
                ChipArea.WordOrder to listOf("V2: inversion after an adverb (I dag rejser jeg)", "Subordinate clauses: at han ikke kommer")
            ),
            chipsOf("Danish")
        )
    }

    @Test
    fun icelandicDrillsItsFourCasesInEveryArea() {
        assertEquals(
            mapOf(
                ChipArea.Cases to listOf(
                    "Nominative → accusative",
                    "Nominative → dative",
                    "Nominative → genitive",
                    "A noun in all four cases: hestur, hest, hesti, hests",
                    "Verbs with a dative or genitive object: hjálpa honum, sakna hans"
                ),
                ChipArea.Articles to listOf("Definite ending in every case: hesturinn, hestinn, hestinum, hestsins"),
                ChipArea.Nouns to listOf("Plural from the singular", "Gender of nouns"),
                ChipArea.Adjectives to listOf("Strong or weak adjective: góður maður, góði maðurinn", "Comparative and superlative: góður, betri, bestur"),
                ChipArea.Verbs to listOf(
                    "Present-tense conjugation",
                    "Strong and weak verbs: kalla → kallaði, bíta → beit",
                    "Middle voice in -st: kalla → kallast"
                ),
                ChipArea.Tenses to listOf("Past tense", "Perfect tense"),
                ChipArea.Pronouns to listOf("Personal pronouns in every case", "Possessive pronouns"),
                ChipArea.Prepositions to listOf("Prepositions and their case"),
                ChipArea.WordOrder to listOf("Verb second (V2) and inversion")
            ),
            chipsOf("Icelandic")
        )
    }

    @Test
    fun finnishDrillsItsLocalCasesAndHasNoArticles() {
        assertEquals(
            mapOf(
                ChipArea.Cases to listOf(
                    "Nominative → genitive",
                    "Nominative → partitive",
                    "Nominative → inessive",
                    "Nominative → elative",
                    "Nominative → illative",
                    "Nominative → adessive",
                    "Nominative → ablative",
                    "Nominative → allative",
                    "Partitive or accusative object: luen kirjaa, luen kirjan"
                ),
                ChipArea.Nouns to listOf("Plural from the singular", "Consonant gradation: katu → kadulla, pöytä → pöydän"),
                ChipArea.Adjectives to listOf("Adjective agreeing with its noun: isossa talossa", "Comparative and superlative: iso, isompi, isoin"),
                ChipArea.Verbs to listOf(
                    "Present-tense conjugation",
                    "Verb types 1–6: puhua, syödä, tulla, haluta, tarvita, vanheta",
                    "Negative verb: en puhu, et puhu, ei puhu"
                ),
                ChipArea.Tenses to listOf("Past tense", "Perfect tense", "Negative past tense: en puhunut", "Conditional: puhuisin"),
                // "My house" is minun taloni: the pronoun's genitive plus an ending, not a possessive pronoun.
                ChipArea.Pronouns to listOf("Personal pronouns in every case", "Possessive suffixes: taloni, talosi, talonsa"),
                // Finnish uses case endings and postpositions where other languages use prepositions.
                ChipArea.Prepositions to listOf("Postpositions with the genitive: talon edessä"),
                ChipArea.WordOrder to listOf("Questions with -ko or -kö: Puhutko suomea?")
            ),
            chipsOf("Finnish")
        )
        assertFalse(ChipArea.Articles in chipsOf("Finnish"))
    }

    @Test
    fun scandinavianLanguagesHaveNoCaseChipsAndNoSpellingChip() {
        listOf("Swedish", "Norwegian", "Danish").forEach { language ->
            val chips = chipsOf(language)
            assertFalse(ChipArea.Cases in chips, "$language has no cases")
            assertTrue(chips.values.flatten().none { "case" in it.lowercase() }, "$language mentions a case")
            assertTrue(chips.values.flatten().none { "spelling" in it.lowercase() }, "$language has a spelling chip")
        }
    }
}
