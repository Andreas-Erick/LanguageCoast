package com.andreaserick.languagecoast.data

/*
 * The example chips under a custom sheet's brief box. Each chip is a short drill in plain words ("Plural
 * from the singular") that a tap adds to the brief. Which chips a language gets comes from its
 * [GrammarProfile]: the drill templates here turn what the profile says the language has (cases, articles,
 * separable verbs...) into chips, so a language never offers a drill for grammar it doesn't have. A language
 * with no profile yet gets the general chips, which fit nearly every language.
 */

/** The areas the chips are grouped by, in the order they're shown. */
enum class ChipArea(val label: String) {
    Cases("Cases"),
    Articles("Articles"),
    Nouns("Nouns & plurals"),
    Adjectives("Adjectives"),
    Verbs("Verbs & conjugation"),
    Tenses("Tenses"),
    Pronouns("Pronouns"),
    Prepositions("Prepositions"),
    WordOrder("Word order"),
    Particles("Particles"),
    Politeness("Politeness")
}

/** One area's chips, as shown in one row under the brief. */
data class BriefChipGroup(val area: ChipArea, val chips: List<String>)

/** The chips of a language that has no profile yet: drills nearly every language has. */
internal val GENERAL_CHIPS: List<BriefChipGroup> = listOf(
    BriefChipGroup(ChipArea.Verbs, listOf("Present-tense conjugation")),
    BriefChipGroup(ChipArea.Tenses, listOf("Past tense")),
    BriefChipGroup(ChipArea.Prepositions, listOf("Prepositions"))
)

/**
 * The example chips for a coast's language, grouped by area in [ChipArea] order, with no empty area.
 *
 * @param language The language's English name, as [Language.name] and a coast store it.
 */
fun briefChips(language: String): List<BriefChipGroup> = GrammarProfiles.of(language)?.let(::briefChips) ?: GENERAL_CHIPS

/** The example chips a grammar profile gives: the templated ones, then the profile's own, by area. */
fun briefChips(profile: GrammarProfile): List<BriefChipGroup> {
    val templated = templateChips(profile)
    return ChipArea.entries.mapNotNull { area ->
        val chips = (templated[area].orEmpty() + profile.extraChips[area].orEmpty()).distinct()
        chips.takeIf { it.isNotEmpty() }?.let { BriefChipGroup(area, it) }
    }
}

/** The chips a profile gets from what its language has, before its own extra chips. */
private fun templateChips(profile: GrammarProfile): Map<ChipArea, List<String>> {
    val hasCases = profile.cases.size > 1
    return mapOf(
        ChipArea.Cases to profile.cases.drop(1).map { "${profile.cases.first().capitalized()} → $it" },
        ChipArea.Articles to when (profile.articles) {
            ArticleSystem.Separate -> listOf("Noun with its definite article", "Noun with its indefinite article") +
                listOfNotNull("Definite article in every case".takeIf { hasCases })
            ArticleSystem.Suffixed -> listOf("Noun in its definite form")
            ArticleSystem.None -> emptyList()
        },
        ChipArea.Nouns to listOfNotNull(
            "Plural from the singular".takeIf { profile.plurals },
            // Without articles, the gender has to be drilled on its own.
            "Gender of nouns".takeIf { profile.genders.isNotEmpty() && profile.articles == ArticleSystem.None }
        ),
        ChipArea.Adjectives to listOfNotNull(
            "Adjective endings".takeIf { profile.adjectiveEndings },
            "Comparative and superlative".takeIf { profile.comparison }
        ),
        ChipArea.Verbs to profile.verbs.map { it.chip },
        ChipArea.Tenses to profile.tenses,
        ChipArea.Pronouns to listOf(if (hasCases) "Personal pronouns in every case" else "Personal pronouns", "Possessive pronouns"),
        ChipArea.Prepositions to listOf(if (hasCases) "Prepositions and their case" else "Prepositions"),
        ChipArea.WordOrder to listOfNotNull("Verb second (V2) and inversion".takeIf { profile.verbSecond }),
        ChipArea.Particles to listOfNotNull("Particles".takeIf { profile.particles }),
        ChipArea.Politeness to listOfNotNull("Polite and plain forms".takeIf { profile.politeness })
    )
}

private fun String.capitalized() = replaceFirstChar { it.uppercaseChar() }

/** How many chips one brief takes: more drills than this at once make the brief too unclear for the model. */
const val BRIEF_CHIPS_MAX = 3

/** What a tapped chip is joined to the rest of the brief with. */
private const val CHIP_SEPARATOR = "; "

/** The brief cut at its separators, without the empty parts. */
private fun parts(brief: String): List<String> = brief.split(';').map { it.trim() }.filter { it.isNotEmpty() }

/**
 * The chips a brief holds, in the brief's order: the parts of it that are one of the [offered] chips word
 * for word (ignoring case). A chip the learner went on to edit is their own words, and no longer counts.
 */
fun chipsInBrief(brief: String, offered: Collection<String>): List<String> =
    parts(brief).mapNotNull { part -> offered.firstOrNull { it.equals(part, ignoreCase = true) } }

/**
 * The brief after tapping a [chip]: a chip it holds already is taken out; any other is added at the end,
 * unless the brief holds [BRIEF_CHIPS_MAX] chips already. What the learner typed is left as it is.
 */
fun toggleChip(brief: String, chip: String, offered: Collection<String>): String {
    val parts = parts(brief)
    return when {
        parts.any { it.equals(chip, ignoreCase = true) } -> parts.filterNot { it.equals(chip, ignoreCase = true) }.joinToString(CHIP_SEPARATOR)
        chipsInBrief(brief, offered).size >= BRIEF_CHIPS_MAX -> brief
        else -> (parts + chip).joinToString(CHIP_SEPARATOR)
    }
}
