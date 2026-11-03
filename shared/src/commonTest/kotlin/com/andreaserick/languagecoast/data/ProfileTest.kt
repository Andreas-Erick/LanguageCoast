package com.andreaserick.languagecoast.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfileTest {

    @Test
    fun fillersLeaveOutBlankFieldsAndTrimTheRest() {
        val profile = Profile(name = " Anna ", city = "", country = "Norway", homeCountry = "Italy", hometown = " ", job = "nurse")

        assertEquals(mapOf("name" to "Anna", "country" to "Norway", "home country" to "Italy", "job" to "nurse"), profile.fillers())
        assertTrue(Profile().isEmpty)
    }

    @Test
    fun everyPlaceholderHasAFieldALabelAndAnExample() {
        Profile.PLACEHOLDERS.forEach { placeholder ->
            assertEquals(mapOf(placeholder to "x"), Profile().with(placeholder, "x").fillers(), placeholder)
            assertTrue(profileLabel(placeholder).startsWith("Your") || profileLabel(placeholder).contains("you"), placeholder)
            assertTrue(profileExample(placeholder).startsWith("e.g."), placeholder)
        }
        assertEquals(Profile(), Profile().with("destination", "Berlin"))
    }

    @Test
    fun theProfileFillsTheAboutMeTopicExceptPerSentenceBlanks() {
        val aboutMe = PhraseBank.theme("About Me")!!
        val profile = Profile(name = "Anna", city = "Oslo", country = "Norway", homeCountry = "Italy", hometown = "Bologna", job = "nurse")
        val filled = aboutMe.sentences.mapNotNull { PhraseBank.fill(it, profile.fillers() + ("language" to "German")) }

        assertTrue("My name is Anna." in filled)
        assertTrue("I'm from Italy." in filled)
        assertTrue("I live in Oslo." in filled)
        assertTrue("I live in Norway now." in filled)
        assertTrue("I was born in Bologna." in filled)
        assertTrue("I work as a nurse." in filled)
        assertTrue(filled.none { "[" in it })
        // "I'm [number] years old." and the like still need a value per sentence.
        assertTrue(filled.size < aboutMe.sentences.size)
    }
}
