package com.andreaserick.languagecoast.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.intl.LocaleList
import com.andreaserick.languagecoast.data.Languages

/** One locale list per offered language, by its English name; made once, since a span is built for every card text shown. */
private val localesByLanguage: Map<String, LocaleList> = Languages.ALL.associate { it.name to LocaleList(it.code) }

/**
 * [text] marked as being written in the language named [languageName] (its English name, as coasts store it).
 * A screen reader then reads it with that language's voice, and the phone picks that language's letter
 * shapes (Japanese kanji instead of the Chinese forms). The mark is a span, because that is what reaches
 * accessibility services; a locale on the text style does not.
 *
 * Returns [text] unmarked when the language isn't one the app offers.
 */
fun inLanguage(text: String, languageName: String): AnnotatedString {
    val locales = localesByLanguage[languageName]
    if (locales == null || text.isEmpty()) return AnnotatedString(text)
    return AnnotatedString(text, SpanStyle(localeList = locales))
}
