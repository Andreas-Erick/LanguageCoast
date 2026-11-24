package com.andreaserick.languagecoast.data

/*
 * Grammar profiles of the Romance and West Germanic languages, Latin and Esperanto. They join
 * [GrammarProfiles.BY_LANGUAGE] there; this file only holds the data, so a language's drills can be read
 * and changed in one place.
 */

/** The profiles in this file, by [Language.name]. */
internal val WESTERN_PROFILES: Map<String, GrammarProfile> =
    mapOf(
        "Dutch" to GrammarProfile(
            articles = ArticleSystem.Separate,
            genders = listOf("common", "neuter"),
            plurals = true,
            adjectiveEndings = true,
            comparison = true,
            verbs = listOf(VerbFeature.Conjugation, VerbFeature.Irregular, VerbFeature.Separable, VerbFeature.Modal, VerbFeature.Reflexive),
            tenses = listOf("Perfect tense", "Simple past", "Future tense"),
            verbSecond = true,
            extraChips = mapOf(
                ChipArea.Articles to listOf("de or het"),
                ChipArea.Nouns to listOf("Diminutives (huis → huisje)"),
                ChipArea.Tenses to listOf("Perfect with hebben or zijn"),
                ChipArea.WordOrder to listOf("Verb at the end after omdat, dat and of")
            )
        ),
        // English articles hardly change, so the drill is which one to use, not its form.
        "English" to GrammarProfile(
            plurals = true,
            comparison = true,
            verbs = listOf(VerbFeature.Conjugation, VerbFeature.Irregular, VerbFeature.Modal, VerbFeature.Passive),
            tenses = listOf("Past simple", "Present perfect", "Present continuous", "Past continuous", "Future with will or going to"),
            extraChips = mapOf(
                ChipArea.Articles to listOf("a or an", "a, the or no article"),
                ChipArea.Verbs to listOf("Phrasal verbs"),
                ChipArea.Tenses to listOf("Past simple or present perfect"),
                ChipArea.WordOrder to listOf("Questions with do and does")
            )
        ),
        // La is the only article and never changes, and nouns have no gender, so neither gets a drill.
        "Esperanto" to GrammarProfile(
            cases = listOf("nominative", "accusative"),
            plurals = true,
            adjectiveEndings = true,
            comparison = true,
            verbs = listOf(VerbFeature.Conjugation),
            tenses = listOf("Past tense (-is)", "Future tense (-os)", "Conditional (-us)"),
            extraChips = mapOf(
                ChipArea.Cases to listOf("Accusative -n of direction (en la domon)"),
                ChipArea.Nouns to listOf("Word endings -o, -a, -e", "Affixes (mal-, -ej-, -ist-)"),
                ChipArea.Pronouns to listOf("Correlatives (kio, tio, ĉio)")
            )
        ),
        "French" to GrammarProfile(
            articles = ArticleSystem.Separate,
            genders = listOf("masculine", "feminine"),
            plurals = true,
            adjectiveEndings = true,
            comparison = true,
            verbs = listOf(VerbFeature.Conjugation, VerbFeature.Irregular, VerbFeature.Reflexive),
            tenses = listOf("Passé composé", "Imparfait", "Future tense", "Conditional", "Present subjunctive"),
            extraChips = mapOf(
                ChipArea.Articles to listOf("Partitive article (du, de la, des)"),
                ChipArea.Adjectives to listOf("Adjective before or after the noun"),
                ChipArea.Tenses to listOf("Passé composé with avoir or être", "Passé composé or imparfait"),
                ChipArea.Pronouns to listOf("Object pronouns (le, lui, y, en)")
            )
        ),
        "Italian" to GrammarProfile(
            articles = ArticleSystem.Separate,
            genders = listOf("masculine", "feminine"),
            plurals = true,
            adjectiveEndings = true,
            comparison = true,
            verbs = listOf(VerbFeature.Conjugation, VerbFeature.Irregular, VerbFeature.Reflexive),
            tenses = listOf("Passato prossimo", "Imperfetto", "Future tense", "Conditional", "Present subjunctive"),
            extraChips = mapOf(
                ChipArea.Articles to listOf("Article with a preposition (del, nella)"),
                ChipArea.Tenses to listOf("Passato prossimo with avere or essere", "Passato prossimo or imperfetto"),
                ChipArea.Pronouns to listOf("Object pronouns (lo, gli, ne)")
            )
        ),
        // Latin has no articles, so its three genders get a drill of their own.
        "Latin" to GrammarProfile(
            cases = listOf("nominative", "genitive", "dative", "accusative", "ablative", "vocative"),
            genders = listOf("masculine", "feminine", "neuter"),
            plurals = true,
            adjectiveEndings = true,
            comparison = true,
            verbs = listOf(VerbFeature.Conjugation, VerbFeature.Irregular, VerbFeature.Passive),
            tenses = listOf("Imperfect", "Future tense", "Perfect tense", "Pluperfect", "Future perfect"),
            extraChips = mapOf(
                ChipArea.Cases to listOf("Ablative absolute"),
                ChipArea.Nouns to listOf("The five declensions"),
                ChipArea.Verbs to listOf("The four conjugations", "Deponent verbs", "Accusative and infinitive"),
                ChipArea.Tenses to listOf("Present subjunctive")
            )
        ),
        "Portuguese" to GrammarProfile(
            articles = ArticleSystem.Separate,
            genders = listOf("masculine", "feminine"),
            plurals = true,
            adjectiveEndings = true,
            comparison = true,
            verbs = listOf(VerbFeature.Conjugation, VerbFeature.Irregular, VerbFeature.Reflexive),
            tenses = listOf("Preterite", "Imperfect", "Future tense", "Conditional", "Present subjunctive"),
            extraChips = mapOf(
                ChipArea.Articles to listOf("Article with a preposition (do, na)"),
                ChipArea.Verbs to listOf("ser or estar", "Personal infinitive"),
                ChipArea.Tenses to listOf("Preterite or imperfect"),
                ChipArea.Prepositions to listOf("por or para")
            )
        ),
        // The definite article is an ending that changes with the case (băiatul, băiatului); the
        // indefinite one is a word of its own (un, o).
        "Romanian" to GrammarProfile(
            cases = listOf("nominative", "genitive-dative", "vocative"),
            articles = ArticleSystem.Suffixed,
            genders = listOf("masculine", "feminine", "neuter"),
            plurals = true,
            adjectiveEndings = true,
            comparison = true,
            verbs = listOf(VerbFeature.Conjugation, VerbFeature.Irregular, VerbFeature.Reflexive),
            tenses = listOf("Perfect compus", "Imperfect", "Future tense", "Present subjunctive (să)"),
            extraChips = mapOf(
                ChipArea.Articles to listOf(
                    "Definite form in the genitive-dative (băiatului)",
                    "Noun with its indefinite article (un, o)",
                    "Possessive article (al, a, ai, ale)"
                )
            )
        ),
        "Spanish" to GrammarProfile(
            articles = ArticleSystem.Separate,
            genders = listOf("masculine", "feminine"),
            plurals = true,
            adjectiveEndings = true,
            comparison = true,
            verbs = listOf(VerbFeature.Conjugation, VerbFeature.Irregular, VerbFeature.Reflexive),
            tenses = listOf("Preterite", "Imperfect", "Present perfect", "Future tense", "Conditional", "Present subjunctive"),
            extraChips = mapOf(
                ChipArea.Verbs to listOf("ser or estar"),
                ChipArea.Tenses to listOf("Preterite or imperfect"),
                ChipArea.Pronouns to listOf("Object pronouns (lo, la, le)"),
                ChipArea.Prepositions to listOf("por or para")
            )
        )
    )
