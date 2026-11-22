// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// What CI's build job runs, as one task to run before pushing: ./gradlew verify
// Android lint fails on errors only; its warnings are in app/build/reports/lint-results-debug.html.
tasks.register("verify") {
    group = "verification"
    description = "Runs lint, the unit tests of both modules and the debug build, as CI does."
    dependsOn(
        ":app:lintDebug",
        ":app:testDebugUnitTest",
        ":shared:testAndroidHostTest",
        ":app:assembleDebug",
    )
}
