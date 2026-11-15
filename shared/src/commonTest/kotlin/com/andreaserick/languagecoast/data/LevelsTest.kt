package com.andreaserick.languagecoast.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LevelsTest {

    private fun island(id: Int, name: String, cards: Int, new: Int) =
        IslandSummary(LanguageIsland(islandId = id, coastId = 1, name = name), cardCount = cards, dueCount = new, newCount = new)

    @Test
    fun everyStarterTopicHasItsLevelAndPhoneSettingsHasNone() {
        assertEquals(Level.A1, levelOf(PhraseBank.theme("Greetings")!!))
        assertEquals(Level.A2, levelOf(PhraseBank.theme("Hotel")!!))
        assertEquals(Level.A2, levelOf(PhraseBank.theme("At Work")!!))
        assertNull(levelOf(PhraseBank.theme("Phone Settings")!!))
        // Every level has topics of its own or islands to write.
        val route = routeFor(emptyList(), emptySet(), emptyMap(), Profile(), emptyMap())
        assertEquals(listOf(Level.A1, Level.A2, Level.B1), route.levels.map { it.level })
        assertTrue(route.levels.all { it.stops.size >= 10 })
        assertEquals(route.levels.sumOf { it.stops.size }, route.levels.flatMap { it.stops }.distinctBy { it.id }.size)
    }

    @Test
    fun aNewCoastStartsAtA1WithItsFirstTopicNext() {
        val route = routeFor(emptyList(), emptySet(), emptyMap(), Profile(), emptyMap())

        assertEquals(Level.A1, route.current.level)
        assertEquals(0f, route.current.progress)
        assertEquals("Greetings", route.next?.name)
        assertEquals(RouteStop.State.Ready, route.next?.state)
        // Nothing is known about the user yet, so the islands about them wait for answers; the B1 themes can be written.
        assertEquals(RouteStop.State.Locked, route.stop(aboutStopId("family"))?.state)
        assertEquals(RouteStop.State.Ready, route.stop(aboutStopId("opinions"))?.state)
        assertEquals(RouteStop.State.Locked, route.stop(levelUpStopId("family", Level.B1))?.state)
    }

    @Test
    fun anIslandCountsAsItsCardsAreLearned() {
        val islands = listOf(island(1, "Greetings", cards = 16, new = 8), island(2, "My Family", cards = 20, new = 20))
        val answers = mapOf("family_parents" to "Maria and Paolo")
        val family = PersonalIslands.byId("family")!!
        val built = mapOf("family" to family.fingerprint(Profile(), answers))

        val route = routeFor(islands, setOf("Greetings"), built, Profile(), answers)

        val greetings = route.stop(topicStopId("Greetings"))!!
        assertEquals(RouteStop.State.Added, greetings.state)
        assertEquals(0.5f, greetings.progress)
        assertEquals(RouteStop.State.Added, route.stop(aboutStopId("family"))?.state)
        assertEquals(0f, route.stop(aboutStopId("family"))?.progress)
        // On the coast, the island about the user can now be taken up a level.
        assertEquals(RouteStop.State.Ready, route.stop(levelUpStopId("family", Level.B1))?.state)
        val a1 = route.levels.first()
        assertEquals(1, a1.onCoast)
        assertEquals(0.5f / a1.stops.size, a1.progress, 0.0001f)
        assertEquals("Getting By", route.next?.name)
    }

    @Test
    fun changedAnswersAndDeletedIslandsShowOnTheRoute() {
        val family = PersonalIslands.byId("family")!!
        val built = mapOf("family" to family.fingerprint(Profile(), mapOf("family_parents" to "Maria")))
        val islands = listOf(island(2, "My Family", cards = 20, new = 0))

        val changed = routeFor(islands, emptySet(), built, Profile(), mapOf("family_parents" to "Maria and Paolo"))
        assertEquals(RouteStop.State.Changed, changed.stop(aboutStopId("family"))?.state)
        assertEquals(1f, changed.stop(aboutStopId("family"))?.progress)

        // Deleted from the coast, the island (and a topic marked as added) can be taken again.
        val deleted = routeFor(emptyList(), setOf("Greetings"), built, Profile(), mapOf("family_parents" to "Maria"))
        assertEquals(RouteStop.State.Ready, deleted.stop(aboutStopId("family"))?.state)
        assertEquals(RouteStop.State.Ready, deleted.stop(topicStopId("Greetings"))?.state)
    }

    @Test
    fun aLevelIsReachedAtNinetyPercentAndTheNextBecomesCurrent() {
        val a1 = routeFor(emptyList(), emptySet(), emptyMap(), Profile(), emptyMap()).levels.first()
        val islands = a1.stops.mapIndexed { i, stop -> island(i + 1, stop.name, cards = 10, new = 0) }
        val topics = a1.stops.filter { it.kind == RouteStop.Kind.Topic }.mapTo(HashSet()) { it.name }
        val answers = mapOf("details_age" to "34")
        val built = mapOf("details" to PersonalIslands.byId("details")!!.fingerprint(Profile(), answers))

        val route = routeFor(islands, topics, built, Profile(), answers)

        assertTrue(route.levels.first().isComplete)
        assertEquals(Level.A2, route.current.level)
        assertNotNull(route.next)
        assertFalse(route.next!!.isOnCoast)
    }

    @Test
    fun wordingBecomesARuleOnlyWhenItIsSet() {
        assertEquals("", speakerRule(Wording.Unspecified, "Spanish"))
        assertTrue("feminine forms" in speakerRule(Wording.Feminine, "Spanish"))
        assertTrue("Spanish" in speakerRule(Wording.Masculine, "Spanish"))
        assertEquals(Wording.Neutral, Wording.fromKey("neutral"))
        assertEquals(Wording.Unspecified, Wording.fromKey(null))

        val prompt = buildStarterTranslationPrompt(PhraseBank.theme("Feelings")!!, "English", "Spanish", null, listOf("I'm tired."), Wording.Feminine)
        assertTrue("The learner is a woman" in prompt)
        assertFalse("The learner is" in buildStarterTranslationPrompt(PhraseBank.theme("Feelings")!!, "English", "Spanish", null, listOf("I'm tired.")))
        assertTrue("The learner is a man" in buildSuggestionPrompt("Spanish", "English", null, 3, emptyList(), emptyList(), 0, wording = Wording.Masculine))
    }

    @Test
    fun aThemeIslandIsWrittenForAnyoneAndALevelUpSaysMore() {
        val opinions = PersonalIslands.byId("opinions")!!
        assertTrue(opinions.isUnlocked(emptyMap()))
        val theme = buildPersonalIslandPrompt(opinions, emptyList(), "English", "German", null, null, 25)
        assertTrue("Level B1" in theme)
        assertTrue("sentences on one theme" in theme)
        assertFalse("told the app" in theme)
        assertFalse("limit, not a target" in theme)

        val family = PersonalIslands.byId("family")!!
        val raised = buildPersonalIslandPrompt(
            family, listOf("Your parents" to "Maria and Paolo"), "English", "German", null, null, 25, level = Level.B1, wording = Wording.Feminine
        )
        assertTrue("Level B1" in raised)
        assertTrue("say more about the same facts" in raised)
        assertTrue("The learner is a woman" in raised)
        assertTrue("Level A2" in buildPersonalIslandPrompt(family, listOf("Your parents" to "Maria"), "English", "German", null, null, 25))
        assertTrue("Level A1" in buildPersonalIslandPrompt(PersonalIslands.byId("details")!!, listOf("How old are you?" to "34"), "English", "German", null, null, 25))
    }

    @Test
    fun aTalkIsAskedForFromTheCardsAndReadBack() {
        val prompt = buildTalkPrompt("My Family", listOf("I have a sister." to "Ich habe eine Schwester."), "English", "German", "Austria", Wording.Masculine)
        assertTrue("- Ich habe eine Schwester. (I have a sister.)" in prompt)
        assertTrue("German as spoken in Austria" in prompt)
        assertTrue("Say nothing the sentences don't say" in prompt)
        assertTrue("The learner is a man" in prompt)

        val talk = parseTalk("Sure!\nTALK:\nIch habe eine Schwester. Sie heißt Giulia.\n\nMEANING:\nI have a sister. Her name is Giulia.")
        assertEquals(IslandTalk("Ich habe eine Schwester. Sie heißt Giulia.", "I have a sister. Her name is Giulia."), talk)
        assertNull(parseTalk("Ich habe eine Schwester."))
        assertNull(parseTalk("TALK:\n\nMEANING:\nI have a sister."))
    }
}
