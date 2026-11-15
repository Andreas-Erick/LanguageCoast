# Third-party software

Language Coast is built with the open-source software below. The same list is shown in the app under Settings > About > Open-source licences (`shared/.../ui/settings/LicencesSheet.kt`); keep the two in step with `gradle/libs.versions.toml`.

| Software | Licence | Project | In |
|---|---|---|---|
| Kotlin, with the kotlinx coroutines, serialization and datetime libraries | Apache License 2.0 | https://github.com/JetBrains/kotlin | Android, iPhone |
| Compose Multiplatform, with its Material 3 components and icons | Apache License 2.0 | https://github.com/JetBrains/compose-multiplatform | Android, iPhone |
| AndroidX: Lifecycle, Navigation, Room, SQLite and DataStore | Apache License 2.0 | https://developer.android.com/jetpack/androidx | Android, iPhone |
| AndroidX: Jetpack Compose, Activity, Core, WorkManager and Splash Screen | Apache License 2.0 | https://developer.android.com/jetpack/androidx | Android |
| SQLite (bundled copy) | Public domain | https://www.sqlite.org/copyright.html | iPhone |
| Koin | Apache License 2.0 | https://github.com/InsertKoinIO/koin | Android, iPhone |
| Ktor | Apache License 2.0 | https://github.com/ktorio/ktor | Android, iPhone |
| Okio | Apache License 2.0 | https://github.com/square/okio | Android, iPhone |
| ML Kit translation and text recognition | ML Kit Terms of Service | https://developers.google.com/ml-kit/terms | Android |
| LiteRT-LM | Apache License 2.0 | https://github.com/google-ai-edge/LiteRT-LM | Android |
| zstd-jni | BSD 2-Clause License (below) | https://github.com/luben/zstd-jni | Android |
| Zstandard | BSD 3-Clause License (below) | https://github.com/facebook/zstd | Android |
| Skia | BSD 3-Clause License (below) | https://skia.org | iPhone |
| ICU | Unicode License | https://github.com/unicode-org/icu | iPhone |
| HarfBuzz | Old MIT License | https://github.com/harfbuzz/harfbuzz | iPhone |
| Nunito typeface | SIL Open Font License 1.1 ([Nunito-OFL.txt](Nunito-OFL.txt)) | https://github.com/googlefonts/nunito | Android, iPhone |

Licence texts: [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0), [SIL Open Font License 1.1](https://openfontlicense.org/open-font-license-official-text/).

## zstd-jni

Copyright (c) 2015-present, Luben Karavelov/ All rights reserved.

BSD-2-Clause License https://opensource.org/license/bsd-2-clause

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

## Zstandard

BSD License

For Zstandard software

Copyright (c) Meta Platforms, Inc. and affiliates. All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.

* Neither the name Facebook, nor Meta, nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

## Skia

Copyright (c) 2011 Google Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.

* Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
