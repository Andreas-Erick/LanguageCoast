import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Code shared by the Android and iOS apps. Android builds work on any OS; the iOS targets need macOS with Xcode
// and are skipped elsewhere.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization) // Backups
    alias(libs.plugins.ksp) // Room annotation processing
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
            api(libs.androidx.room.runtime)
            api(libs.androidx.datastore.preferences)
            api(project.dependencies.platform(libs.koin.bom))
            api(libs.koin.core)
            implementation(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime) // In reviewOutcomes's signature
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.androidx.sqlite.framework)
        }
        iosMain.dependencies {
            implementation(libs.androidx.sqlite.bundled)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Room generates the database code once per platform.
dependencies {
    listOf("kspAndroid", "kspIosArm64", "kspIosSimulatorArm64").forEach { add(it, libs.androidx.room.compiler) }
}
