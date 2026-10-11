import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Code shared by the Android and iOS apps. Android builds work on any OS; the iOS targets need macOS with Xcode
// and are skipped elsewhere.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization) // Backups
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
        withHostTest {}
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // Entities keep their Room annotations; the database itself is still in the app.
            implementation(libs.androidx.room.runtime)
            implementation(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime) // In reviewOutcomes's signature
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
