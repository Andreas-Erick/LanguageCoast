package com.andreaserick.languagecoast

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The intent the widget and the reminder open the app with ([MainActivity.resumeIntent]). It has to look like
 * the launcher icon's, or Android 8 to 10 put a second copy of the app on top of the one that is open.
 */
@RunWith(AndroidJUnit4::class)
class ResumeIntentTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun itIsTheLaunchersIntentAndNamesNoPackage() {
        val intent = MainActivity.resumeIntent(context)

        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertTrue("The launcher category is missing", intent.hasCategory(Intent.CATEGORY_LAUNCHER))
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertNull("The launcher's intent names no package", intent.getPackage())
    }

    @Test
    fun itBringsTheOpenAppForwardInsteadOfPuttingACopyOnTop() {
        val flags = MainActivity.resumeIntent(context).flags

        assertTrue("A new task is not asked for", (flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
        assertTrue("A copy on top is not ruled out", (flags and Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0)
        assertEquals("The task would be cleared", 0, flags and Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
