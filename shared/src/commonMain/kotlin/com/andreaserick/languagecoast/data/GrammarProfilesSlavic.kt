package com.andreaserick.languagecoast.data

/*
 * Grammar profiles of the Slavic languages. They share most of their grammar: cases (except Bulgarian),
 * three genders and no articles, adjectives that agree, and verbs in aspect pairs. [slavic] holds that
 * common ground, and each language lists its own cases, tenses and drills.
 */

/** A Slavic language with cases and no articles, so its genders get a drill of their own. */
private fun slavic(
    cases: List<String>,
    tenses: List<String>,
    genders: List<String> = listOf("masculine", "feminine", "neuter"),
    extraChips: Map<ChipArea, List<String>> = emptyMap()
) = GrammarProfile(
    cases = cases,
    genders = genders,
    plurals = true,
    adjectiveEndings = true,
    comparison = true,
    verbs = listOf(VerbFeature.Conjugation, VerbFeature.Irregular, VerbFeature.Aspect, VerbFeature.Reflexive),
    tenses = tenses,
    extraChips = extraChips
)

/** The seven cases of Bosnian, Croatian and Serbian, in the order their grammars teach them. */
private val BCS_CASES = listOf("nominative", "genitive", "dative", "accusative", "vocative", "instrumental", "locative")

/** Drills Bosnian, Croatian and Serbian share: the short pronouns and where they go. */
private val BCS_EXTRAS = mapOf(
    ChipArea.Pronouns to listOf("Short pronouns (ga, mu, joj)"),
    ChipArea.WordOrder to listOf("Short words in second place (sam, je, se)")
)

/** The profiles in this file, by [Language.name]. */
internal val SLAVIC_PROFILES: Map<String, GrammarProfile> = mapOf(
    "Bosnian" to slavic(BCS_CASES, listOf("Perfect tense (past)", "Future tense (ću)"), extraChips = BCS_EXTRAS),
    // Bulgarian lost its noun cases (only pronouns keep them) and gained a definite ending instead.
    "Bulgarian" to GrammarProfile(
        articles = ArticleSystem.Suffixed,
        genders = listOf("masculine", "feminine", "neuter"),
        plurals = true,
        adjectiveEndings = true,
        comparison = true,
        verbs = listOf(VerbFeature.Conjugation, VerbFeature.Irregular, VerbFeature.Aspect, VerbFeature.Reflexive),
        tenses = listOf("Past tense (aorist)", "Imperfect", "Perfect tense", "Future tense (ще)"),
        extraChips = mapOf(
            ChipArea.Articles to listOf("Short or full article (-а or -ът)", "Article on the adjective (новата къща)"),
            ChipArea.Verbs to listOf("да instead of an infinitive (искам да чета)", "Reported forms (той бил)"),
            ChipArea.Pronouns to listOf("Short object pronouns (го, ѝ, му)")
        )
    ),
    "Croatian" to slavic(BCS_CASES, listOf("Perfect tense (past)", "Future tense (ću)"), extraChips = BCS_EXTRAS),
    "Czech" to slavic(
        cases = listOf("nominative", "genitive", "dative", "accusative", "vocative", "locative", "instrumental"),
        tenses = listOf("Past tense", "Future tense"),
        genders = listOf("masculine animate", "masculine inanimate", "feminine", "neuter"),
        extraChips = mapOf(
            ChipArea.Verbs to listOf("Verbs of motion (jít or chodit)"),
            ChipArea.Tenses to listOf("Conditional (bych, bys)")
        )
    ),
    "Polish" to slavic(
        cases = listOf("nominative", "genitive", "dative", "accusative", "instrumental", "locative", "vocative"),
        tenses = listOf("Past tense", "Future tense"),
        extraChips = mapOf(
            ChipArea.Cases to listOf("Numbers and the case after them (dwa koty, pięć kotów)"),
            ChipArea.Nouns to listOf("Plural of men (studenci, oni)"),
            ChipArea.Verbs to listOf("Verbs of motion (iść or chodzić)"),
            ChipArea.Tenses to listOf("Conditional (-by)")
        )
    ),
    "Russian" to slavic(
        cases = listOf("nominative", "genitive", "dative", "accusative", "instrumental", "prepositional"),
        tenses = listOf("Past tense", "Future tense"),
        extraChips = mapOf(
            ChipArea.Cases to listOf("Numbers and the case after them (два стола, пять столов)"),
            ChipArea.Verbs to listOf("Verbs of motion (идти or ходить)")
        )
    ),
    "Serbian" to slavic(
        BCS_CASES,
        listOf("Perfect tense (past)", "Future tense (ću)"),
        extraChips = BCS_EXTRAS + (ChipArea.Verbs to listOf("da + present or the infinitive"))
    ),
    "Slovak" to slavic(
        cases = listOf("nominative", "genitive", "dative", "accusative", "locative", "instrumental"),
        tenses = listOf("Past tense", "Future tense"),
        genders = listOf("masculine animate", "masculine inanimate", "feminine", "neuter"),
        extraChips = mapOf(ChipArea.Tenses to listOf("Conditional (by)"))
    ),
    "Ukrainian" to slavic(
        cases = listOf("nominative", "genitive", "dative", "accusative", "instrumental", "locative", "vocative"),
        tenses = listOf("Past tense", "Future tense (буду читати, читатиму)"),
        extraChips = mapOf(ChipArea.Verbs to listOf("Verbs of motion (іти or ходити)"))
    )
)
