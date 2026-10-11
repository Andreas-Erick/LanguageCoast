package com.andreaserick.languagecoast.util

import com.andreaserick.languagecoast.data.Languages

/** dict.cc only has dictionaries between English or German and another language. */
private val DICT_CC_HUB_CODES = setOf("en", "de")

/**
 * The dict.cc subdomain for looking up words of [targetLang], e.g. "enis" for English/Icelandic.
 *
 * Uses the native/target pair when dict.cc has it; otherwise falls back to the target language's
 * English dictionary (e.g. French speakers learning Spanish get "enes"). Returns null when dict.cc
 * has no dictionary for the target language at all.
 *
 * @param nativeLang The user's native language name.
 * @param targetLang The name of the language being learned.
 */
fun getDictCcPrefix(nativeLang: String, targetLang: String): String? =
    dictCcPair(nativeLang, targetLang) ?: dictCcPair("English", targetLang)

/** Builds a dict.cc search URL for [word], or null when dict.cc can't look up [targetLang] words. */
fun dictCcSearchUrl(word: String, nativeLang: String, targetLang: String): String? {
    val prefix = getDictCcPrefix(nativeLang, targetLang) ?: return null
    return "https://$prefix.dict.cc/?s=${formEncode(word)}"
}

/** [text] encoded for a URL query, as `java.net.URLEncoder` does: spaces become `+`, other bytes `%XX` (UTF-8). */
internal fun formEncode(text: String): String = buildString {
    for (byte in text.encodeToByteArray()) {
        val c = (byte.toInt() and 0xFF).toChar()
        when {
            c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in ".-*_" -> append(c)
            c == ' ' -> append('+')
            else -> append('%').append((byte.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0'))
        }
    }
}

/** The subdomain for a pair dict.cc has, which is the two codes in alphabetical order (e.g. "bgde"). */
private fun dictCcPair(firstLang: String, secondLang: String): String? {
    val first = Languages.byName(firstLang)?.takeIf { it.onDictCc } ?: return null
    val second = Languages.byName(secondLang)?.takeIf { it.onDictCc } ?: return null
    if (first == second) return null
    if (first.code !in DICT_CC_HUB_CODES && second.code !in DICT_CC_HUB_CODES) return null
    return listOf(first.code, second.code).sorted().joinToString("")
}
