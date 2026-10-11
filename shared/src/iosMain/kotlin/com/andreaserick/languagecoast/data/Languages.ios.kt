package com.andreaserick.languagecoast.data

import platform.Foundation.NSDiacriticInsensitiveSearch
import platform.Foundation.NSString
import platform.Foundation.stringByFoldingWithOptions

@Suppress("CAST_NEVER_SUCCEEDS") // Kotlin strings are bridged to NSString on iOS.
internal actual fun removeAccents(text: String): String =
    (text as NSString).stringByFoldingWithOptions(NSDiacriticInsensitiveSearch, locale = null)
