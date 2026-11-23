package com.andreaserick.languagecoast.data

/*
 * Grammar profiles of the languages that belong to no family with another in the app's list: Albanian
 * and Greek, Hungarian and Turkish, Japanese and Korean. Hungarian and Turkish build words from endings
 * and use postpositions; Japanese and Korean carry their grammar in particles and change their verbs with
 * politeness, and have neither cases nor articles.
 */

/** The profiles in this file, by [Language.name]. */
internal val OTHER_PROFILES: Map<String, GrammarProfile> = mapOf(
    // The definite article is an ending that changes with the case (djali, djalit, djalin).
    "Albanian" to GrammarProfile(
        cases = listOf("nominative", "genitive", "dative", "accusative", "ablative"),
        articles = ArticleSystem.Suffixed,
        genders = listOf("masculine", "feminine"),
        plurals = true,
        adjectiveEndings = true,
        comparison = true,
        verbs = listOf(VerbFeature.Conjugation, VerbFeature.Irregular, VerbFeature.Passive),
        tenses = listOf("Past tense (aorist)", "Imperfect", "Perfect tense", "Future tense (do të)"),
        extraChips = mapOf(
            ChipArea.Articles to listOf("Definite form in every case (djali, djalit, djalin)"),
            ChipArea.Adjectives to listOf("Linking article (i mirë, e mirë)"),
            ChipArea.Verbs to listOf("Subjunctive with të")
        )
    ),
    "Greek" to GrammarProfile(
        cases = listOf("nominative", "genitive", "accusative", "vocative"),
        articles = ArticleSystem.Separate,
        genders = listOf("masculine", "feminine", "neuter"),
        plurals = true,
        adjectiveEndings = true,
        comparison = true,
        verbs = listOf(VerbFeature.Conjugation, VerbFeature.Irregular, VerbFeature.Aspect, VerbFeature.Passive),
        tenses = listOf("Simple past (aorist)", "Imperfect", "Future tense", "Present perfect"),
        extraChips = mapOf(
            ChipArea.Tenses to listOf("Ongoing or one-off future (θα γράφω, θα γράψω)", "Subjunctive with να"),
            ChipArea.Pronouns to listOf("Short object pronouns (το, του, τη)")
        )
    ),
    // No gender at all. The articles (a or az, egy) never change, so only the choice between a and az is
    // a drill. Of the many cases, the three for "where", "where to" and "where from" are drilled together.
    "Hungarian" to GrammarProfile(
        cases = listOf("nominative", "accusative", "dative"),
        plurals = true,
        comparison = true,
        verbs = listOf(VerbFeature.Conjugation, VerbFeature.Irregular),
        tenses = listOf("Past tense", "Future tense (fog)", "Conditional (-na, -ne)"),
        prepositions = false,
        extraChips = mapOf(
            ChipArea.Cases to listOf(
                "Where: -ban, -on, -nál",
                "Where to: -ba, -ra, -hoz",
                "Where from: -ból, -ról, -tól"
            ),
            ChipArea.Articles to listOf("a or az"),
            ChipArea.Nouns to listOf("Vowel harmony in endings (házban, kertben)", "Possessive endings (ház → házam)"),
            ChipArea.Verbs to listOf("Definite or indefinite conjugation (látok, látom)", "Verb prefixes (megy → elmegy)"),
            ChipArea.Prepositions to listOf("Postpositions (alatt, mellett, után)")
        )
    ),
    // Japanese nouns have no plural ending and "my" is 私の, a particle, not a pronoun of its own. Verbs
    // don't change with the person, so the drills are about their forms instead of a conjugation table.
    "Japanese" to GrammarProfile(
        tenses = listOf("Past tense", "Ongoing action (〜ている)"),
        particles = true,
        politeness = true,
        prepositions = false,
        possessivePronouns = false,
        extraChips = mapOf(
            ChipArea.Nouns to listOf("Counters (三本, 三枚)"),
            ChipArea.Adjectives to listOf("い- and な-adjectives", "Comparing with より and 一番"),
            ChipArea.Verbs to listOf("Verb groups (る-verbs and う-verbs)", "て-form (食べる → 食べて)", "Negative form (行く → 行かない)"),
            ChipArea.Particles to listOf("は or が", "に or で"),
            ChipArea.Politeness to listOf("Honorific and humble verbs (keigo)")
        )
    ),
    "Korean" to GrammarProfile(
        tenses = listOf("Past tense", "Future tense (-을 거예요)", "Ongoing action (-고 있다)"),
        particles = true,
        politeness = true,
        prepositions = false,
        possessivePronouns = false,
        extraChips = mapOf(
            ChipArea.Nouns to listOf("Counters (세 개, 두 명)"),
            ChipArea.Verbs to listOf("Present-tense endings (가다 → 가요)", "Irregular stems (ㅂ, ㄷ, 르)", "Negation with 안 or 못"),
            ChipArea.Particles to listOf("은/는 or 이/가", "Object particle 을/를"),
            ChipArea.Politeness to listOf("Speech levels (해요 or 합니다)", "Honorific -시-")
        )
    ),
    // No gender and no articles (bir is a number that also does the job of "a"), but six cases, and
    // every ending follows the vowel of the word before it.
    "Turkish" to GrammarProfile(
        cases = listOf("nominative", "accusative", "dative", "locative", "ablative", "genitive"),
        plurals = true,
        comparison = true,
        verbs = listOf(VerbFeature.Conjugation, VerbFeature.Passive),
        tenses = listOf("Past tense (-di)", "Present continuous (-iyor)", "Future tense (-ecek)", "Aorist (-ir)"),
        prepositions = false,
        extraChips = mapOf(
            ChipArea.Nouns to listOf("Vowel harmony in endings (evler, kitaplar)", "Possessive endings (ev → evim)"),
            ChipArea.Verbs to listOf("Negative verbs (gelmiyorum)", "Questions with mi"),
            ChipArea.Prepositions to listOf("Postpositions (için, gibi, ile)")
        )
    )
)
