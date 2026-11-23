package com.andreaserick.languagecoast.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BriefChipsTest {

    private fun chipsOf(language: String) = briefChips(language).associate { it.area to it.chips }

    @Test
    fun aLanguageWithoutAProfileGetsTheGeneralChips() {
        assertEquals(
            mapOf(
                ChipArea.Verbs to listOf("Present-tense conjugation"),
                ChipArea.Tenses to listOf("Past tense"),
                ChipArea.Prepositions to listOf("Prepositions")
            ),
            chipsOf("Klingon")
        )
    }

    @Test
    fun everyLanguageInTheAppHasAProfileOrGetsTheGeneralChipsOnPurpose() {
        val names = Languages.ALL.map { it.name }.toSet()
        names.forEach { name ->
            val profiled = name in GrammarProfiles.BY_LANGUAGE
            val fallback = name in GrammarProfiles.GENERAL_ON_PURPOSE
            assertTrue(profiled != fallback, "$name must have a profile or be a deliberate fallback, not both or neither")
            // Whichever it is, the learner sees chips.
            assertTrue(briefChips(name).isNotEmpty(), "$name shows no chips")
        }
        // No profile or fallback for a language the app doesn't offer (a misspelt name would never be found).
        assertEquals(emptySet(), (GrammarProfiles.BY_LANGUAGE.keys + GrammarProfiles.GENERAL_ON_PURPOSE) - names)
    }

    @Test
    fun tappingChipsBuildsTheBriefUpToThreeAndTappingAgainTakesOneOut() {
        val offered = briefChips("German").flatMap { it.chips }
        var brief = ""
        brief = toggleChip(brief, "Plural from the singular", offered)
        brief = toggleChip(brief, "Noun with its definite article", offered)
        assertEquals("Plural from the singular; Noun with its definite article", brief)

        brief = toggleChip(brief, "Modal verbs", offered)
        // A fourth chip would make the brief too much for one sheet, so it isn't added.
        assertEquals(brief, toggleChip(brief, "Simple past", offered))
        assertEquals(listOf("Plural from the singular", "Noun with its definite article", "Modal verbs"), chipsInBrief(brief, offered))

        // Tapping a chip that is in already takes it out, and makes room again.
        brief = toggleChip(brief, "Noun with its definite article", offered)
        assertEquals("Plural from the singular; Modal verbs", brief)
    }

    @Test
    fun chipsAddToWhatTheLearnerTypedAndLeaveItAlone() {
        val offered = briefChips("German").flatMap { it.chips }
        val typed = "only with words about food"
        val brief = toggleChip(typed, "Nominative → dative", offered)
        assertEquals("only with words about food; Nominative → dative", brief)
        // Typed words aren't chips and don't count towards the three.
        assertEquals(listOf("Nominative → dative"), chipsInBrief(brief, offered))
        assertEquals(typed, toggleChip(brief, "Nominative → dative", offered))
        // A chip the learner edited is their own words now, so it no longer counts as tapped.
        assertEquals(emptyList(), chipsInBrief("Nominative → dative with mit", offered))
        // A brief ending in its own separator or spaces doesn't get a doubled one.
        assertEquals("food; Modal verbs", toggleChip("food; ", "Modal verbs", offered))
    }

    @Test
    fun aChipThatWouldMakeTheBriefTooLongIsNotAdded() {
        val offered = briefChips("German").flatMap { it.chips }
        val long = "x".repeat(BRIEF_MAX_LENGTH - 5)
        // Cut at the brief's length, the chip would be a fragment of itself; it isn't added at all.
        assertEquals(long, toggleChip(long, "Modal verbs", offered))
        // One that fits still is, and one that is in can always be taken out.
        val fits = "x".repeat(BRIEF_MAX_LENGTH - "; Modal verbs".length)
        val withChip = toggleChip(fits, "Modal verbs", offered)
        assertEquals(BRIEF_MAX_LENGTH, withChip.length)
        assertEquals(fits, toggleChip(withChip, "Modal verbs", offered))
    }

    @Test
    fun theLineAboveTheChipsSaysWhyNoMoreCanBeAdded() {
        val offered = briefChips("German").flatMap { it.chips }
        assertEquals("Or tap up to 3 examples to add them.", chipsHint("", offered))
        // Three chips in: the limit is the reason.
        val three = "Plural from the singular; Modal verbs; Simple past"
        assertEquals("That's 3 examples: tap one to take it out.", chipsHint(three, offered))
        // No room left for any chip: the length is the reason, and the line says so.
        assertEquals("Your brief is too long to add more examples.", chipsHint("x".repeat(BRIEF_MAX_LENGTH - 5), offered))
        // Room for a short chip still: the usual line.
        assertEquals("Or tap up to 3 examples to add them.", chipsHint("x".repeat(BRIEF_MAX_LENGTH - "; Modal verbs".length), offered))
    }

    @Test
    fun aLanguageOnlyGetsChipsForGrammarItHas() {
        // Like Japanese: no cases, no articles, no plurals, but particles and politeness.
        val japaneseLike = briefChips(
            GrammarProfile(verbs = listOf(VerbFeature.Conjugation), tenses = listOf("Past tense"), particles = true, politeness = true)
        ).associate { it.area to it.chips }
        assertEquals(
            listOf(ChipArea.Verbs, ChipArea.Tenses, ChipArea.Pronouns, ChipArea.Prepositions, ChipArea.Particles, ChipArea.Politeness),
            japaneseLike.keys.toList()
        )
        assertEquals(listOf("Personal pronouns", "Possessive pronouns"), japaneseLike[ChipArea.Pronouns])
        assertEquals(listOf("Prepositions"), japaneseLike[ChipArea.Prepositions])

        // Like Swedish: a suffixed article and genders, no cases.
        val swedishLike = briefChips(GrammarProfile(articles = ArticleSystem.Suffixed, genders = listOf("common", "neuter"), plurals = true))
            .associate { it.area to it.chips }
        assertEquals(null, swedishLike[ChipArea.Cases])
        assertEquals(listOf("Noun in its definite form"), swedishLike[ChipArea.Articles])
        assertEquals(listOf("Plural from the singular"), swedishLike[ChipArea.Nouns])

        // Like Russian: cases and genders but no articles, so the gender gets a drill of its own.
        val russianLike = briefChips(GrammarProfile(cases = listOf("nominative", "instrumental"), genders = listOf("masculine", "feminine", "neuter")))
            .associate { it.area to it.chips }
        assertEquals(listOf("Nominative → instrumental"), russianLike[ChipArea.Cases])
        assertEquals(null, russianLike[ChipArea.Articles])
        assertEquals(listOf("Gender of nouns"), russianLike[ChipArea.Nouns])
    }

    @Test
    fun germanShowsItsFullChipSetAreaByArea() {
        assertEquals(
            mapOf(
                ChipArea.Cases to listOf(
                    "Nominative → accusative",
                    "Nominative → dative",
                    "Nominative → genitive",
                    "Accusative or dative after two-way prepositions"
                ),
                ChipArea.Articles to listOf(
                    "Noun with its definite article",
                    "Noun with its indefinite article",
                    "Definite article in every case"
                ),
                ChipArea.Nouns to listOf("Plural from the singular", "Weak nouns (der Junge → den Jungen)"),
                ChipArea.Adjectives to listOf("Adjective endings", "Comparative and superlative"),
                ChipArea.Verbs to listOf(
                    "Present-tense conjugation",
                    "Irregular verbs",
                    "Separable verbs",
                    "Modal verbs",
                    "Reflexive verbs"
                ),
                ChipArea.Tenses to listOf(
                    "Perfect tense",
                    "Simple past",
                    "Future tense",
                    "Perfect with haben or sein",
                    "Subjunctive II (hätte, wäre, würde)"
                ),
                ChipArea.Pronouns to listOf("Personal pronouns in every case", "Possessive pronouns"),
                ChipArea.Prepositions to listOf("Prepositions and their case"),
                ChipArea.WordOrder to listOf("Verb second (V2) and inversion", "Verb at the end after weil, dass and ob")
            ),
            chipsOf("German")
        )
        // The rows come in the fixed area order, whatever order the profile lists them in.
        assertEquals(ChipArea.entries.filter { it in chipsOf("German") }, briefChips("German").map { it.area })
    }
}
