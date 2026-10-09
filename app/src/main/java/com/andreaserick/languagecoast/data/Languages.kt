package com.andreaserick.languagecoast.data

import java.text.Normalizer

/**
 * A language the app can translate into and from.
 *
 * @property name English name; this is what coasts and settings store, so it must never change.
 * @property nativeName The language's name for itself (e.g. "Deutsch"), shown and searchable in the picker.
 * @property code ISO 639-1 code, as used by dict.cc subdomains.
 * @property onDictCc Whether dict.cc has dictionaries for this language.
 */
data class Language(
    val name: String,
    val nativeName: String,
    val code: String,
    val onDictCc: Boolean = true
)

object Languages {

    /**
     * Every language offered in the app: all languages dict.cc has dictionaries for, plus a few that
     * Gemini translates well but dict.cc doesn't cover. Sorted by English name.
     */
    val ALL: List<Language> = listOf(
        Language("Albanian", "Shqip", "sq"),
        Language("Bosnian", "Bosanski", "bs"),
        Language("Bulgarian", "Български", "bg"),
        Language("Croatian", "Hrvatski", "hr"),
        Language("Czech", "Čeština", "cs"),
        Language("Danish", "Dansk", "da"),
        Language("Dutch", "Nederlands", "nl"),
        Language("English", "English", "en"),
        Language("Esperanto", "Esperanto", "eo"),
        Language("Finnish", "Suomi", "fi"),
        Language("French", "Français", "fr"),
        Language("German", "Deutsch", "de"),
        Language("Greek", "Ελληνικά", "el"),
        Language("Hungarian", "Magyar", "hu"),
        Language("Icelandic", "Íslenska", "is"),
        Language("Italian", "Italiano", "it"),
        Language("Japanese", "日本語", "ja", onDictCc = false),
        Language("Korean", "한국어", "ko", onDictCc = false),
        Language("Latin", "Latina", "la"),
        Language("Norwegian", "Norsk", "no"),
        Language("Polish", "Polski", "pl"),
        Language("Portuguese", "Português", "pt"),
        Language("Romanian", "Română", "ro"),
        Language("Russian", "Русский", "ru"),
        Language("Serbian", "Српски", "sr"),
        Language("Slovak", "Slovenčina", "sk"),
        Language("Spanish", "Español", "es"),
        Language("Swedish", "Svenska", "sv"),
        Language("Turkish", "Türkçe", "tr"),
        Language("Ukrainian", "Українська", "uk")
    )

    private val byName = ALL.associateBy { it.name }

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

private fun String.normalizedForSearch(): String =
    Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
