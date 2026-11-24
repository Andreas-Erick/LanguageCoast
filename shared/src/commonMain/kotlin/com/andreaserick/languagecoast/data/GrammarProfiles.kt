package com.andreaserick.languagecoast.data

/*
 * What each language's grammar has, as far as a custom sheet's example chips need to know. A profile is
 * pure data: the drill templates in BriefChips.kt turn it into chips, so adding a language means adding one
 * entry to [GrammarProfiles.BY_LANGUAGE] (and taking its name out of [GrammarProfiles.GENERAL_ON_PURPOSE]).
 * Drills only one language has, such as German two-way prepositions, go in [GrammarProfile.extraChips].
 */

/** How a language marks a noun as definite or indefinite. */
enum class ArticleSystem {
    /** Articles are words of their own before the noun: der Mann, ein Mann. */
    Separate,

    /** The definite article is an ending on the noun: bil → bilen. */
    Suffixed,

    /** No articles at all: Finnish, Japanese, Russian. */
    None
}

/** A verb feature worth a drill of its own, with the chip that drills it. */
enum class VerbFeature(val chip: String) {
    Conjugation("Present-tense conjugation"),
    Irregular("Irregular verbs"),
    Separable("Separable verbs"),
    Modal("Modal verbs"),
    Reflexive("Reflexive verbs"),
    Aspect("Perfective or imperfective verb"),
    Passive("Passive voice"),
    MiddleVoice("Middle voice")
}

/**
 * A language's grammar, as far as choosing drills for it goes. Every field defaults to "the language doesn't
 * have it", so a profile names only what its language has.
 *
 * @property cases The cases worth drilling, by English name, the base case (usually the nominative) first.
 *     Each other case gets a "Nominative → dative" chip; none at all means the language gets no case chips.
 * @property articles How definiteness is marked; [ArticleSystem.None] gets no article chips.
 * @property genders The grammatical genders by name. A language with genders but no articles gets a gender
 *     chip of its own, since there is no article to learn the gender with.
 * @property plurals Whether nouns have a plural form of their own (Japanese and Korean nouns mostly don't).
 * @property adjectiveEndings Whether adjectives change their ending to agree with the noun.
 * @property comparison Whether adjectives have comparative and superlative forms.
 * @property verbs The verb features worth a drill, in the order the chips are shown.
 * @property tenses The tenses worth forming, by the name a chip shows, present tense left out (conjugation
 *     covers it).
 * @property verbSecond Whether the finite verb comes second in a main clause, so a time phrase first swaps
 *     the subject behind it.
 * @property particles Whether grammar is carried by particles after words (Japanese は and が).
 * @property politeness Whether verbs or endings change with how polite the speaker is.
 * @property extraChips Drills only this language has, added after the templated chips of their area.
 * @property prepositions Whether the language has prepositions. One with postpositions (Hungarian, Turkish)
 *     or particles (Japanese, Korean) has none, and names its own drill in [extraChips] if it wants one.
 * @property possessivePronouns Whether "my" and "mine" are pronouns of their own, not a particle or ending.
 */
data class GrammarProfile(
    val cases: List<String> = emptyList(),
    val articles: ArticleSystem = ArticleSystem.None,
    val genders: List<String> = emptyList(),
    val plurals: Boolean = false,
    val adjectiveEndings: Boolean = false,
    val comparison: Boolean = false,
    val verbs: List<VerbFeature> = emptyList(),
    val tenses: List<String> = emptyList(),
    val verbSecond: Boolean = false,
    val particles: Boolean = false,
    val politeness: Boolean = false,
    val extraChips: Map<ChipArea, List<String>> = emptyMap(),
    val prepositions: Boolean = true,
    val possessivePronouns: Boolean = true
)

object GrammarProfiles {

    /** Standard German, as taught from A1 on. */
    val GERMAN = GrammarProfile(
        cases = listOf("nominative", "accusative", "dative", "genitive"),
        articles = ArticleSystem.Separate,
        genders = listOf("masculine", "feminine", "neuter"),
        plurals = true,
        adjectiveEndings = true,
        comparison = true,
        verbs = listOf(VerbFeature.Conjugation, VerbFeature.Irregular, VerbFeature.Separable, VerbFeature.Modal, VerbFeature.Reflexive),
        tenses = listOf("Perfect tense", "Simple past", "Future tense"),
        verbSecond = true,
        extraChips = mapOf(
            ChipArea.Cases to listOf("Accusative or dative after two-way prepositions"),
            ChipArea.Nouns to listOf("Weak nouns (der Junge → den Jungen)"),
            ChipArea.Tenses to listOf("Perfect with haben or sein", "Subjunctive II (hätte, wäre, würde)"),
            ChipArea.WordOrder to listOf("Verb at the end after weil, dass and ob")
        )
    )

    /** The hand-written profiles, by [Language.name]. */
    val BY_LANGUAGE: Map<String, GrammarProfile> = mapOf(
        "German" to GERMAN
    ) + WESTERN_PROFILES + SLAVIC_PROFILES + OTHER_PROFILES + NordicGrammarProfiles.BY_LANGUAGE

    /**
     * The languages of [Languages.ALL] that get the general chips on purpose, because no profile has been
     * written for them yet: none today. A test checks that every language is either here or in
     * [BY_LANGUAGE], so a language added to the app has to be placed in one of the two.
     */
    val GENERAL_ON_PURPOSE: Set<String> = emptySet()

    /** The profile of a language by its English name, or null when it gets the general chips. */
    fun of(language: String): GrammarProfile? = BY_LANGUAGE[language]
}
