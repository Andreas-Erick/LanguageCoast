package com.andreaserick.languagecoast.data

/**
 * A language the app can translate into and from.
 *
 * @property name English name; this is what coasts and settings store, so it must never change.
 * @property nativeName The language's name for itself (e.g. "Deutsch"), shown and searchable in the picker.
 * @property code ISO 639-1 code, as used by dict.cc subdomains.
 * @property flagCountry ISO 3166 code of the country whose flag stands for this language. It's often not the
 *     same as [code] (Japanese is "ja", Japan is "JP"). Null for a language no country stands for, like Esperanto.
 * @property onDictCc Whether dict.cc has dictionaries for this language.
 */
data class Language(
    val name: String,
    val nativeName: String,
    val code: String,
    val flagCountry: String? = null,
    val onDictCc: Boolean = true
) {
    /** The flag as an emoji, or null if it has none (see [flagCountry]). */
    val flag: String? get() = flagCountry?.let(::flagEmoji)
}

/**
 * The flag emoji for a two-letter country code. A flag is the code's letters written as a pair of
 * "regional indicator" symbols, which take two UTF-16 chars each.
 */
private fun flagEmoji(country: String): String = buildString {
    country.forEach { letter ->
        append(Char(0xD83C))
        append(Char(0xDDE6 + (letter - 'A')))
    }
}

object Languages {

    /**
     * Every language offered in the app: all languages dict.cc has dictionaries for, plus a few that
     * Gemini translates well but dict.cc doesn't cover. Sorted by English name.
     */
    val ALL: List<Language> = listOf(
        Language("Albanian", "Shqip", "sq", flagCountry = "AL"),
        Language("Bosnian", "Bosanski", "bs", flagCountry = "BA"),
        Language("Bulgarian", "Български", "bg", flagCountry = "BG"),
        Language("Croatian", "Hrvatski", "hr", flagCountry = "HR"),
        Language("Czech", "Čeština", "cs", flagCountry = "CZ"),
        Language("Danish", "Dansk", "da", flagCountry = "DK"),
        Language("Dutch", "Nederlands", "nl", flagCountry = "NL"),
        Language("English", "English", "en", flagCountry = "GB"),
        Language("Esperanto", "Esperanto", "eo"),
        Language("Finnish", "Suomi", "fi", flagCountry = "FI"),
        Language("French", "Français", "fr", flagCountry = "FR"),
        Language("German", "Deutsch", "de", flagCountry = "DE"),
        Language("Greek", "Ελληνικά", "el", flagCountry = "GR"),
        Language("Hungarian", "Magyar", "hu", flagCountry = "HU"),
        Language("Icelandic", "Íslenska", "is", flagCountry = "IS"),
        Language("Italian", "Italiano", "it", flagCountry = "IT"),
        Language("Japanese", "日本語", "ja", flagCountry = "JP", onDictCc = false),
        Language("Korean", "한국어", "ko", flagCountry = "KR", onDictCc = false),
        Language("Latin", "Latina", "la", flagCountry = "VA"),
        Language("Norwegian", "Norsk", "no", flagCountry = "NO"),
        Language("Polish", "Polski", "pl", flagCountry = "PL"),
        Language("Portuguese", "Português", "pt", flagCountry = "PT"),
        Language("Romanian", "Română", "ro", flagCountry = "RO"),
        Language("Russian", "Русский", "ru", flagCountry = "RU"),
        Language("Serbian", "Српски", "sr", flagCountry = "RS"),
        Language("Slovak", "Slovenčina", "sk", flagCountry = "SK"),
        Language("Spanish", "Español", "es", flagCountry = "ES"),
        Language("Swedish", "Svenska", "sv", flagCountry = "SE"),
        Language("Turkish", "Türkçe", "tr", flagCountry = "TR"),
        Language("Ukrainian", "Українська", "uk", flagCountry = "UA")
    )

    private val byName = ALL.associateBy { it.name }

    /** The language with English name [name], or null if it isn't offered. */
    fun byName(name: String): Language? = byName[name]
}

/**
 * Languages matching [query] by English or native name, ignoring case and accents
 * (so "islen" finds "Íslenska"). Names starting with the query come before other matches.
 */
fun List<Language>.search(query: String): List<Language> {
    val q = query.normalizedForSearch()
    if (q.isEmpty()) return this
    val names = { language: Language -> listOf(language.name, language.nativeName).map { it.normalizedForSearch() } }
    val prefixMatches = filter { language -> names(language).any { it.startsWith(q) } }
    val otherMatches = filter { language -> language !in prefixMatches && names(language).any { q in it } }
    return prefixMatches + otherMatches
}

/** Lowercase and without accents or surrounding spaces, for comparing names in [search]. */
private fun String.normalizedForSearch(): String = removeAccents(trim().lowercase())

/** [text] without accents and other combining marks ("íslenska" → "islenska"), using the platform's Unicode tables. */
internal expect fun removeAccents(text: String): String
