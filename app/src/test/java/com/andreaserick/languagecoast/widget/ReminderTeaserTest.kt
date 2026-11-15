package com.andreaserick.languagecoast.widget

import com.andreaserick.languagecoast.data.Flashcard
import com.andreaserick.languagecoast.data.PersonalIslands
import com.andreaserick.languagecoast.data.PlacedCard
import com.andreaserick.languagecoast.notifications.teaserCard
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which card the daily reminder may quote (teaserCard in notifications/StudyReminderWorker.kt). The reminder
 * and the widget are the two places where the app speaks outside its own screens, so their tests sit together.
 */
class ReminderTeaserTest {

    private val now = 1_800_000_000_000L
    private val day = LocalDate(2026, 11, 15)

    private fun card(id: Int, island: String, due: Long?) = PlacedCard(
        card = Flashcard(cardId = id, islandId = 1, nativeText = "native $id", targetText = "target $id", due = due),
        islandName = island,
        islandEmoji = null,
        coastId = 1,
        language = "German"
    )

    @Test
    fun aCardThatWasNeverStudiedIsNotQuoted() {
        assertNull(teaserCard(listOf(card(1, "Food", due = null)), now, day))
    }

    @Test
    fun aCardThatIsNotDueYetIsNotQuoted() {
        assertNull(teaserCard(listOf(card(1, "Food", due = now + 1)), now, day))
    }

    @Test
    fun aCardFromAnIslandAboutTheUserIsNotQuoted() {
        val aboutTheUser = PersonalIslands.ALL.first { it.isAboutUser }.name
        val cards = listOf(card(1, aboutTheUser, due = now - 1), card(2, "Food", due = now - 1))

        // Whatever the day, only the card that says nothing about the user is quoted.
        (1..28).forEach { dayOfMonth ->
            assertEquals(2, teaserCard(cards, now, LocalDate(2026, 11, dayOfMonth))?.card?.cardId)
        }
        assertNull(teaserCard(cards.take(1), now, day))
    }

    @Test
    fun aCardFromAnIslandOnAThemeForAnyoneCanBeQuoted() {
        val theme = PersonalIslands.WRITTEN.first { !it.isAboutUser }.name

        assertEquals(1, teaserCard(listOf(card(1, theme, due = now - 1)), now, day)?.card?.cardId)
    }

    @Test
    fun theQuotedCardChangesFromDayToDay() {
        val cards = listOf(card(1, "Food", due = now - 1), card(2, "Food", due = now - 1), card(3, "Food", due = now))

        val quoted = (15..17).map { dayOfMonth -> teaserCard(cards, now, LocalDate(2026, 11, dayOfMonth))?.card?.cardId }

        assertEquals(setOf(1, 2, 3), quoted.toSet())
    }
}
