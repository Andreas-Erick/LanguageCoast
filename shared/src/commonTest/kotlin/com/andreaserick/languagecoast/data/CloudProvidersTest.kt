package com.andreaserick.languagecoast.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What every entry of [CLOUD_PROVIDERS] must hold. A new provider is checked by these tests as soon as it is
 * added, without a test of its own.
 */
class CloudProvidersTest {

    private val entries = CLOUD_PROVIDERS.values

    @Test
    fun everyCloudProviderHasExactlyOneEntry() {
        assertEquals(TranslationProvider.entries.filter { it.isCloud }.toSet(), CLOUD_PROVIDERS.keys)
        entries.forEach { assertEquals(it.provider, CLOUD_PROVIDERS.getValue(it.provider).provider) }
    }

    @Test
    fun aListedProviderStartsOnAModelFromItsListAndATypedOneHasNoList() {
        entries.forEach {
            assertEquals(it.hasModelList, it.models.isNotEmpty(), it.provider.name)
            if (it.hasModelList) assertTrue(it.defaultModel in it.models, "${it.provider.name}: ${it.defaultModel}")
            assertTrue(it.defaultModel.isNotBlank(), it.provider.name)
        }
        assertEquals(entries.filter { it.hasModelList }.map { it.provider }, MODEL_LIST_PROVIDERS)
    }

    @Test
    fun aBlankModelIsSentAsTheDefault() {
        entries.forEach { assertEquals(it.normalizeModel(it.defaultModel), it.modelOrDefault("  "), it.provider.name) }
    }

    @Test
    fun everyWireIsBuiltUnderTheProvidersName() {
        entries.forEach {
            val wire = assertNotNull(wireFor(TranslationEngine.Cloud(it.provider, "key", "")), it.provider.name)
            assertEquals(it.provider.label, wire.providerName)
        }
    }

    @Test
    fun keysStayOnThePhoneAndModelsAreBackedUp() {
        val deviceOnly = SettingKeys.deviceOnly.map { it.name }.toSet()
        val backedUp = SettingKeys.backedUp.map { it.key.name }.toSet()
        entries.forEach {
            assertTrue(it.keyPreference in SettingKeys.SECRET_KEYS, it.provider.name)
            assertTrue(it.keyPreference.name in deviceOnly, it.provider.name)
            assertTrue(it.modelPreference.name in backedUp, it.provider.name)
        }
        val names = entries.flatMap { listOf(it.keyPreference.name, it.modelPreference.name) }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun keyAndModelComeBackFromTheSettings() = runTest {
        val settings = memorySettings()
        entries.forEach {
            assertEquals("", settings.key(it.provider).first(), it.provider.name)
            assertEquals(it.normalizeModel(it.defaultModel), settings.model(it.provider).first(), it.provider.name)

            settings.setKey(it.provider, "key-${it.provider.name}")
            val picked = it.models.lastOrNull() ?: "vendor/model"
            settings.setModel(it.provider, picked)

            assertEquals("key-${it.provider.name}", settings.key(it.provider).first())
            assertEquals(it.normalizeModel(picked), settings.model(it.provider).first())
        }
        // Each provider kept its own key.
        entries.forEach { assertEquals("key-${it.provider.name}", settings.key(it.provider).first()) }
    }

    @Test
    fun theCompanyIsNamedAndThePagesAreSecure() {
        entries.forEach {
            assertTrue(it.company.isNotBlank(), it.provider.name)
            assertTrue(it.keyUrl.startsWith("https://"), it.provider.name)
            assertTrue(it.privacyPolicyUrl.startsWith("https://"), it.provider.name)
            it.modelList?.let { list -> assertTrue(list.url.startsWith("https://"), it.provider.name) }
        }
    }
}
