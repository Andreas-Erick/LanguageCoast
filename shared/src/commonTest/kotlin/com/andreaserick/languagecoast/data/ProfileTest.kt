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
    fun previewReadsLikeTheSentencesWillAndSkipsWhatIsMissing() {
        assertEquals(null, profilePreview(Profile()))
        assertEquals("My name is Anna.", profilePreview(Profile(name = "Anna")))
        assertEquals("I live in Oslo, Norway. I was born in Bologna.", profilePreview(Profile(city = "Oslo", country = "Norway", hometown = "Bologna")))
        assertEquals(
            "My name is Anna. I work as a nurse. I live in Oslo, Norway. I'm from Italy, born in Bologna.",
            profilePreview(Profile(name = "Anna", city = "Oslo", country = "Norway", homeCountry = "Italy", hometown = "Bologna", job = "nurse"))
        )
        Profile.PLACEHOLDERS.forEach { assertTrue(profileShortLabel(it).first().isUpperCase(), it) }
    }

    @Test
    fun aJobThatStartsWithAVowelTakesAn() {
        assertEquals("an", indefiniteArticle("engineer"))
        assertEquals("an", indefiniteArticle(" Architect"))
        assertEquals("a", indefiniteArticle("nurse"))
        // "A university lecturer", "a UX designer": u is left alone.
        assertEquals("a", indefiniteArticle("university lecturer"))
        assertEquals("a", indefiniteArticle(""))

        assertEquals("I work as an engineer.", profilePreview(Profile(job = "engineer")))
        assertEquals("I work as a nurse.", profilePreview(Profile(job = "nurse")))
        assertEquals("I work as an engineer.", PhraseBank.fill("I work as a [job].", mapOf("job" to "engineer")))
        assertEquals("I work as an Accountant.", PhraseBank.fill("I work as a [job].", mapOf("job" to " Accountant ")))
        assertEquals("I work as a teacher.", PhraseBank.fill("I work as a [job].", mapOf("job" to "teacher")))
        // Without a job the sentence stays unusable, and other blanks are not touched.
        assertEquals(null, PhraseBank.fill("I work as a [job].", emptyMap()))
        assertEquals("I live in Oslo.", PhraseBank.fill("I live in [city].", mapOf("city" to "Oslo")))
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
