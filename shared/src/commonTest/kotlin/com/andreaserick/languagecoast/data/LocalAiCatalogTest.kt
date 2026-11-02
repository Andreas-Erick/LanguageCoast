package com.andreaserick.languagecoast.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalAiCatalogTest {

    @Test
    fun everyModelDownloadsFromItsRepoAsALiteRtLmFile() {
        LocalAiCatalog.MODELS.forEach { model ->
            assertTrue(model.fileName.endsWith(".litertlm"), model.fileName)
            assertEquals("https://huggingface.co/${model.huggingFaceRepo}/resolve/main/${model.fileName}", model.downloadUrl)
            assertTrue(model.sizeMb > 100, "${model.id} size")
            assertTrue(model.minRamMb <= model.recommendedRamMb, "${model.id} RAM")
        }
    }

    @Test
    fun idsAndFileNamesAreUnique() {
        assertEquals(LocalAiCatalog.MODELS.size, LocalAiCatalog.MODELS.map { it.id }.toSet().size)
        assertEquals(LocalAiCatalog.MODELS.size, LocalAiCatalog.MODELS.map { it.fileName }.toSet().size)
    }

    @Test
    fun onlyGemma3IsGated() {
        // The others download without an account; Gemma 3 needs its licence accepted on Hugging Face.
        assertEquals(listOf("gemma3-1b-it"), LocalAiCatalog.MODELS.filter { it.gated }.map { it.id })
    }

    @Test
    fun theDefaultModelIsTheSmallestOpenOne() {
        val default = LocalAiCatalog.byId(LocalAiCatalog.DEFAULT_MODEL_ID)!!
        assertTrue(!default.gated)
        assertEquals(default.sizeMb, LocalAiCatalog.MODELS.minOf { it.sizeMb })
    }
}
