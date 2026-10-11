package com.andreaserick.languagecoast.speech

import com.andreaserick.languagecoast.data.Languages
import kotlinx.coroutines.flow.Flow

/** Reads text aloud in a given language. */
interface Speaker {
    /** Whether [language] (an English language name) can be read aloud; emits again once the speech engine is ready. */
    fun canSpeak(language: String): Flow<Boolean>

    /** Reads [text] aloud in [language], interrupting anything still being read. Does nothing if that isn't possible. */
    fun speak(text: String, language: String)

    /** Stops reading. */
    fun stop()
}

/** The language code voices use for [language], or null for a language the app doesn't know. */
internal fun speechLanguageCode(language: String): String? =
    // Voices are installed for Norwegian Bokmål ("nb"); "no" usually finds none.
    Languages.byName(language)?.code?.let { code -> if (code == "no") "nb" else code }
