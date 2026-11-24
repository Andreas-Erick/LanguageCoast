package com.andreaserick.languagecoast.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.andreaserick.languagecoast.ui.theme.SurfaceRaised
import com.andreaserick.languagecoast.ui.theme.TextPrimary
import com.andreaserick.languagecoast.ui.theme.TextSecondary

/** A piece of software the app is built with: what it is, the licence it comes under, and its project's page. */
private class Library(val name: String, val licence: String, val url: String)

private const val APACHE = "Apache License 2.0"
private const val APACHE_URL = "https://www.apache.org/licenses/LICENSE-2.0"
private const val OFL_URL = "https://openfontlicense.org/open-font-license-official-text/"

/**
 * The software in this platform's app. What only the Android app ships (the reminder's WorkManager, ML Kit,
 * LiteRT-LM, zstd) is left out on iPhone. What only the iPhone app ships is left out on Android, which uses
 * the phone's own: a copy of SQLite, and Skia with ICU and HarfBuzz, which draw the app there.
 *
 * Keep it in step with gradle/libs.versions.toml when a library is added or dropped.
 */
private val LIBRARIES: List<Library> = listOfNotNull(
    Library("Kotlin, with the kotlinx coroutines, serialization and datetime libraries", APACHE, "https://github.com/JetBrains/kotlin"),
    Library("Compose Multiplatform, with its Material 3 components and icons", APACHE, "https://github.com/JetBrains/compose-multiplatform"),
    Library(
        if (runsOnAndroid) "AndroidX: Jetpack Compose, Activity, Core, Lifecycle, Navigation, Room, SQLite, DataStore, WorkManager and Splash Screen"
        else "AndroidX: Lifecycle, Navigation, Room, SQLite and DataStore",
        APACHE,
        "https://developer.android.com/jetpack/androidx"
    ),
    Library("SQLite", "Public domain", "https://www.sqlite.org/copyright.html").takeIf { !runsOnAndroid },
    Library("Koin", APACHE, "https://github.com/InsertKoinIO/koin"),
    Library("Ktor", APACHE, "https://github.com/ktorio/ktor"),
    Library("Okio", APACHE, "https://github.com/square/okio"),
    Library("ML Kit translation and text recognition", "ML Kit Terms of Service", "https://developers.google.com/ml-kit/terms")
        .takeIf { runsOnAndroid },
    Library("LiteRT-LM", APACHE, "https://github.com/google-ai-edge/LiteRT-LM").takeIf { runsOnAndroid },
    Library("zstd-jni", "BSD 2-Clause License, printed below", "https://github.com/luben/zstd-jni").takeIf { runsOnAndroid },
    Library("Zstandard", "BSD 3-Clause License, printed below", "https://github.com/facebook/zstd").takeIf { runsOnAndroid },
    Library("Skia", "BSD 3-Clause License, printed below", "https://skia.org").takeIf { !runsOnAndroid },
    Library("ICU", "Unicode License", "https://github.com/unicode-org/icu").takeIf { !runsOnAndroid },
    Library("HarfBuzz", "Old MIT License", "https://github.com/harfbuzz/harfbuzz").takeIf { !runsOnAndroid },
    Library(
        "Nunito typeface",
        "SIL Open Font License 1.1. Copyright 2014 The Nunito Project Authors",
        "https://github.com/googlefonts/nunito"
    )
)

/** The notice the BSD licence asks to be reproduced with every copy of zstd-jni. */
private val ZSTD_JNI_NOTICE = """
    zstd-jni

    Copyright (c) 2015-present, Luben Karavelov/ All rights reserved.

    BSD-2-Clause License https://opensource.org/license/bsd-2-clause

    Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.

    * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.

    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
""".trimIndent()

/** The notice the BSD licence asks to be reproduced with every copy of Zstandard, which zstd-jni contains. */
private val ZSTANDARD_NOTICE = """
    BSD License

    For Zstandard software

    Copyright (c) Meta Platforms, Inc. and affiliates. All rights reserved.

    Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.

    * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.

    * Neither the name Facebook, nor Meta, nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.

    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
""".trimIndent()

/** The notice the BSD licence asks to be reproduced with every copy of Skia, which draws the iPhone app. */
private val SKIA_NOTICE = """
    Skia

    Copyright (c) 2011 Google Inc. All rights reserved.

    Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.

    * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.

    * Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.

    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
""".trimIndent()

/**
 * A bottom sheet naming the open-source software the app is built with, each with its licence and a link to
 * its project, the licence texts, and in full the notices that have to travel with the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LicencesSheet(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    // An address that couldn't be opened is shown under its row: a snackbar would sit behind this sheet.
    var unopened by remember { mutableStateOf<String?>(null) }
    val openLink: (String) -> Unit = { url -> unopened = url.takeIf { runCatching { uriHandler.openUri(url) }.isFailure } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        // One scrolling column for everything, so the end of a long list can always be reached.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            Text("Open-source licences", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Language Coast is built with the software below. Tap a name to open its project.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            HorizontalDivider(color = SurfaceRaised, modifier = Modifier.padding(vertical = 12.dp))
            LIBRARIES.forEach { library ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClickLabel = "open the project's page", role = Role.Button) { openLink(library.url) }
                        .padding(vertical = 8.dp)
                ) {
                    Text(library.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    Text(library.licence, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    if (unopened == library.url) Text(library.url, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }

            HorizontalDivider(color = SurfaceRaised, modifier = Modifier.padding(vertical = 12.dp))
            Text("Licence texts", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            SettingsLink(APACHE) { openLink(APACHE_URL) }
            SettingsLink("SIL Open Font License 1.1") { openLink(OFL_URL) }
            unopened?.takeIf { it == APACHE_URL || it == OFL_URL }?.let { url ->
                Text(url, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            // The notices that have to travel with this platform's app.
            val notices = if (runsOnAndroid) listOf(ZSTD_JNI_NOTICE, ZSTANDARD_NOTICE) else listOf(SKIA_NOTICE)
            notices.forEach { notice ->
                Text(
                    notice,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}
