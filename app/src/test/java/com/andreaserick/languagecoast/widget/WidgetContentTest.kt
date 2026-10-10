package com.andreaserick.languagecoast.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetContentTest {

    @Test
    fun dueCardsAndAStreakToKeep() {
        val content = widgetContent(dueCount = 12, streak = 5, studiedToday = false)

        assertEquals("🔥 5", content.streak)
        assertEquals("12", content.due)
        assertEquals("cards due", content.dueLabel)
        assertEquals("Study today to keep your streak", content.footer)
    }

    @Test
    fun oneCardDueAfterStudyingToday() {
        val content = widgetContent(dueCount = 1, streak = 1, studiedToday = true)

        assertEquals("card due", content.dueLabel)
        assertEquals("Studied today. Nice work!", content.footer)
    }

    @Test
    fun nothingDueAndNoStreak() {
        val caughtUp = widgetContent(dueCount = 0, streak = 0, studiedToday = false)
        assertEquals("⚓ 0", caughtUp.streak)
        assertEquals("✓", caughtUp.due)
        assertEquals("all caught up", caughtUp.dueLabel)
        assertEquals("Add cards to start studying", caughtUp.footer)

        assertEquals("Finish an island to start a streak", widgetContent(dueCount = 3, streak = 0, studiedToday = false).footer)
    }
}
