package com.andreaserick.languagecoast.data

import java.text.Normalizer

private val COMBINING_MARKS = Regex("\\p{Mn}+")

internal actual fun removeAccents(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
