import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Code shared by the Android and iOS apps. Android builds work on any OS; the iOS targets need macOS with Xcode
// and are skipped elsewhere.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization) // Backups
    alias(libs.plugins.ksp) // Room annotation processing
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform) // Shared UI and its resources (font, logo)
}

kotlin {
    android {
        namespace = "com.andreaserick.languagecoast.shared"
        compileSdk {
            version = release(37) {
                minorApiLevel = 1
            }
        }
        minSdk = 26
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
        // Runs commonTest on the JVM, so ./gradlew :shared:testAndroidHostTest works without a device.
        // Compose resources (font, logo) are packaged as Android resources.
        androidResources {
            enable = true
        }
        withHostTest {
            // android.util.Log does nothing in tests instead of throwing.
            isReturnDefaultValues = true
        }
    }

    // The iOS app links the shared code as one framework, named Shared.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.androidx.room.runtime)
            api(libs.androidx.datastore.preferences)
            api(project.dependencies.platform(libs.koin.bom))
            api(libs.koin.core)
            api(libs.koin.core.viewmodel)
            api(libs.androidx.lifecycle.viewmodel)
            api(libs.androidx.lifecycle.viewmodel.savedstate)
            api(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.components.resources)
            implementation(libs.jetbrains.navigation.compose)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            api(libs.kotlinx.datetime) // In reviewOutcomes's signature
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.androidx.sqlite.framework)
            implementation(libs.ktor.client.android)
            implementation(libs.mlkit.translate)
        }
        iosMain.dependencies {
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Generated accessors for composeResources (Res.font.nunito, Res.drawable.…).
compose.resources {
    packageOfResClass = "com.andreaserick.languagecoast.resources"
}

// Room generates the database code once per platform.
dependencies {
    listOf("kspAndroid", "kspIosArm64", "kspIosSimulatorArm64").forEach { add(it, libs.androidx.room.compiler) }
}
