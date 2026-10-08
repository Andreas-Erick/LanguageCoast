package com.andreaserick.languagecoast.util

import java.net.URLEncoder

private val DICT_CC_LANGUAGE_CODES = mapOf(
    "English" to "en",
    "Spanish" to "es",
    "French" to "fr",
    "German" to "de",
    "Italian" to "it",
    "Japanese" to "ja",
    "Korean" to "ko",
    "Icelandic" to "is",
    "Norwegian" to "no"
)

/**
 * Determines the dict.cc subdomain for a language pair, e.g. "esen" for Spanish/English.
 * dict.cc puts the non-English language first.
 *
 * @param nativeLang The user's native language name.
 * @param targetLang The name of the language being learned.
 */
fun getDictCcPrefix(nativeLang: String, targetLang: String): String {
    val nativeCode = DICT_CC_LANGUAGE_CODES[nativeLang] ?: "en"
    val targetCode = DICT_CC_LANGUAGE_CODES[targetLang] ?: "de"

    return when {
        nativeCode == "en" -> "${targetCode}en"
        targetCode == "en" -> "${nativeCode}en"
        else -> "$nativeCode$targetCode" // e.g. "dees" for German/Spanish
    }
}

/** Builds a dict.cc search URL for [word] using the given language pair. */
fun dictCcSearchUrl(word: String, nativeLang: String, targetLang: String): String {
    val prefix = getDictCcPrefix(nativeLang, targetLang)
    return "https://$prefix.dict.cc/?s=${URLEncoder.encode(word, "UTF-8")}"
}
