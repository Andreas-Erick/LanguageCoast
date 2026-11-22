package com.andreaserick.languagecoast.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Clock

/**
 * The copy of the settings that travels with the phone's own backup ([AndroidSettingsSnapshot]), written and
 * read back on a device. Every "phone" here is a folder of its own in the cache, so the settings of the app
 * installed on the test device, and their copy, are never touched.
 */
@RunWith(AndroidJUnit4::class)
class AndroidSettingsSnapshotTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "settings-snapshot-test")
    private var phones = 0

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    /** A phone's files folder, the settings stored in it, and the copy of them. Each has a settings file of its own. */
    private inner class Phone {
        val files: File = File(root, "phone-${System.nanoTime()}-${phones++}").apply { mkdirs() }
        val settings: SettingsRepository = DataStoreSettingsRepository(
            createSettingsDataStore(File(files, SETTINGS_FILE_PATH).absolutePath),
            Clock.System,
            TimeZone.UTC
        )
        val snapshot = AndroidSettingsSnapshot(
            object : ContextWrapper(context) {
                override fun getFilesDir(): File = files
            },
            settings
        )
        val copy: File get() = File(files, SettingsSnapshot.FILE_NAME)
    }

    @Test
    fun aRestoredPhoneGetsItsSettingsBackButNoKey() = runBlocking<Unit> {
        val old = Phone()
        old.settings.setNativeLanguage("German")
        old.settings.setKey(TranslationProvider.Gemini, "secret-gemini-key")
        old.snapshot.save()

        assertFalse("The key is in the copy", old.copy.readText().contains("secret-gemini-key"))

        // The phone's backup brings the copy along, and not the settings file.
        val restored = Phone()
        old.copy.copyTo(restored.copy)
        assertTrue(restored.snapshot.restoreIsDue())
        restored.snapshot.restoreIfEmpty()

        assertEquals("German", restored.settings.nativeLanguage.first())
        assertEquals("", restored.settings.key(TranslationProvider.Gemini).first())
        assertFalse(restored.snapshot.restoreIsDue())
    }

    @Test
    fun aPhoneWithoutSettingsNeverReplacesAGoodCopy() = runBlocking<Unit> {
        val old = Phone()
        old.settings.setNativeLanguage("German")
        old.snapshot.save()

        // The app is left on the new phone before anything was brought back or set.
        val restored = Phone()
        old.copy.copyTo(restored.copy)
        restored.snapshot.save()

        assertEquals("German", SettingsSnapshot.decode(restored.copy.readText())?.nativeLanguage)
    }

    @Test
    fun settingsThatAreThereAreNotReplacedByTheCopy() = runBlocking<Unit> {
        val old = Phone()
        old.settings.setNativeLanguage("German")
        old.snapshot.save()

        val other = Phone()
        other.settings.setNativeLanguage("French")
        old.copy.copyTo(other.copy)
        other.snapshot.restoreIfEmpty()

        assertFalse(other.snapshot.restoreIsDue())
        assertEquals("French", other.settings.nativeLanguage.first())
    }

    @Test
    fun aCopyThatWasCutOffIsPassedOver() = runBlocking<Unit> {
        val phone = Phone()
        phone.copy.writeText("{\"nativeLanguage\": \"Germ")

        phone.snapshot.restoreIfEmpty()

        assertTrue(phone.settings.isEmpty())
    }
}
