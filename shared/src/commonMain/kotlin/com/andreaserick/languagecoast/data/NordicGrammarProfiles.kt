package com.andreaserick.languagecoast.data

/*
 * The Nordic languages' grammar profiles: the maintainer's focus languages, so they get the deepest chip
 * sets. Where a template's wording is too vague for these languages (the suffixed article, adjective
 * agreement, the passive), its flag is left off and the drill is written out in [GrammarProfile.extraChips]
 * with the language's own example words, so a learner sees at a glance what the chip asks for.
 *
 * Swedish, Norwegian and Danish have no cases, so they get no case chips; their gender shows in en/ett, so
 * they get no separate gender chip either.
 */
object NordicGrammarProfiles {

    /** Standard Swedish. */
    val SWEDISH = GrammarProfile(
        plurals = true,
        tenses = listOf("Past tense", "Perfect tense", "Pluperfect tense", "Future with ska or kommer att"),
        extraChips = mapOf(
            ChipArea.Articles to listOf(
                "en or ett: en bil, ett hus",
                "Definite ending: bil → bilen",
                "Definite plural: bilar → bilarna",
                "Double definiteness: den stora bilen"
            ),
            ChipArea.Nouns to listOf("Plural groups: flickor, bilar, parker, äpplen, hus"),
            ChipArea.Adjectives to listOf("Adjective agreement: stor, stort, stora", "Comparative and superlative: stor, större, störst"),
            ChipArea.Verbs to listOf(
                "Verb groups: past and supine (tala → talade, har talat)",
                "Strong verbs: skriva → skrev, har skrivit",
                "s-passive: boken läses"
            ),
            ChipArea.Pronouns to listOf("sin or hans: Han tog sin bil"),
            ChipArea.WordOrder to listOf("V2: inversion after an adverb (I dag åker jag)", "Subordinate clauses: att han inte kommer")
        )
    )

    /** Norwegian Bokmål, the written standard most learners learn; its feminine ei/-a is kept. */
    val NORWEGIAN = GrammarProfile(
        plurals = true,
        tenses = listOf("Past tense", "Perfect tense", "Pluperfect tense", "Future with skal or kommer til å"),
        extraChips = mapOf(
            ChipArea.Articles to listOf(
                "en, ei or et: en bil, ei jente, et hus",
                "Definite ending: bil → bilen, jente → jenta",
                "Definite plural: biler → bilene",
                "Double definiteness: den store bilen"
            ),
            ChipArea.Nouns to listOf("Plural groups: biler, hus, menn"),
            ChipArea.Adjectives to listOf("Adjective agreement: stor, stort, store", "Comparative and superlative: stor, større, størst"),
            ChipArea.Verbs to listOf(
                "Verb groups: past and perfect (kaste → kastet, har kastet)",
                "Strong verbs: skrive → skrev, har skrevet",
                "s-passive: boken leses"
            ),
            ChipArea.Pronouns to listOf("sin or hans: Han tok bilen sin", "Possessive after the noun: bilen min"),
            ChipArea.WordOrder to listOf("V2: inversion after an adverb (I dag reiser jeg)", "Subordinate clauses: at han ikke kommer")
        )
    )

    /**
     * Standard Danish. Unlike Swedish and Norwegian it has no double definiteness: after an adjective the
     * noun loses its ending (den store bil), which is the drill Danish learners get wrong.
     */
    val DANISH = GrammarProfile(
        plurals = true,
        tenses = listOf("Past tense", "Perfect tense", "Pluperfect tense", "Perfect with have or være: har spist, er gået"),
        extraChips = mapOf(
            ChipArea.Articles to listOf(
                "en or et: en bil, et hus",
                "Definite ending: bil → bilen",
                "Definite plural: biler → bilerne",
                "Definite with an adjective: den store bil"
            ),
            ChipArea.Nouns to listOf("Plural groups: biler, huse, år"),
            ChipArea.Adjectives to listOf("Adjective agreement: stor, stort, store", "Comparative and superlative: stor, større, størst"),
            ChipArea.Verbs to listOf(
                "Verb groups: past and perfect (lave → lavede, har lavet)",
                "Strong verbs: skrive → skrev, har skrevet",
                "s-passive: bogen læses"
            ),
            ChipArea.Pronouns to listOf("sin or hans: Han tog sin bil"),
            ChipArea.WordOrder to listOf("V2: inversion after an adverb (I dag rejser jeg)", "Subordinate clauses: at han ikke kommer")
        )
    )

    /**
     * Modern Icelandic. Its article is an ending that is declined with the noun, and it has no indefinite
     * article, so its one article chip drills the definite ending in every case.
     */
    val ICELANDIC = GrammarProfile(
        cases = listOf("nominative", "accusative", "dative", "genitive"),
        genders = listOf("masculine", "feminine", "neuter"),
        plurals = true,
        verbs = listOf(VerbFeature.Conjugation),
        tenses = listOf("Past tense", "Perfect tense"),
        verbSecond = true,
        extraChips = mapOf(
            ChipArea.Cases to listOf(
                "A noun in all four cases: hestur, hest, hesti, hests",
                "Verbs with a dative or genitive object: hjálpa honum, sakna hans"
            ),
            ChipArea.Articles to listOf("Definite ending in every case: hesturinn, hestinn, hestinum, hestsins"),
            ChipArea.Adjectives to listOf("Strong or weak adjective: góður maður, góði maðurinn", "Comparative and superlative: góður, betri, bestur"),
            ChipArea.Verbs to listOf("Strong and weak verbs: kalla → kallaði, bíta → beit", "Middle voice in -st: kalla → kallast")
        )
    )

    /**
     * Standard Finnish: no articles and no genders, but cases do the work of most prepositions. The local
     * cases (inside: -ssa, -sta, -an; on: -lla, -lta, -lle) each get a chip of their own.
     */
    val FINNISH = GrammarProfile(
        cases = listOf("nominative", "genitive", "partitive", "inessive", "elative", "illative", "adessive", "ablative", "allative"),
        plurals = true,
        verbs = listOf(VerbFeature.Conjugation),
        tenses = listOf("Past tense", "Perfect tense", "Negative past tense: en puhunut", "Conditional: puhuisin"),
        // Postpositions do the work of prepositions (talon edessä).
        prepositions = false,
        // "My house" is minun taloni: the pronoun's genitive and a possessive suffix.
        possessivePronouns = false,
        extraChips = mapOf(
            ChipArea.Cases to listOf("Partitive or accusative object: luen kirjaa, luen kirjan"),
            ChipArea.Nouns to listOf("Consonant gradation: katu → kadulla, pöytä → pöydän"),
            ChipArea.Adjectives to listOf("Adjective agreeing with its noun: isossa talossa", "Comparative and superlative: iso, isompi, isoin"),
            ChipArea.Verbs to listOf("Verb types 1–6: puhua, syödä, tulla, haluta, tarvita, vanheta", "Negative verb: en puhu, et puhu, ei puhu"),
            ChipArea.Pronouns to listOf("Possessive suffixes: taloni, talosi, talonsa"),
            ChipArea.Prepositions to listOf("Postpositions with the genitive: talon edessä"),
            ChipArea.WordOrder to listOf("Questions with -ko or -kö: Puhutko suomea?")
        )
    )

    /** The Nordic profiles, by [Language.name], for [GrammarProfiles.BY_LANGUAGE]. */
    val BY_LANGUAGE: Map<String, GrammarProfile> = mapOf(
        "Swedish" to SWEDISH,
        "Norwegian" to NORWEGIAN,
        "Danish" to DANISH,
        "Icelandic" to ICELANDIC,
        "Finnish" to FINNISH
    )
}
