package com.andreaserick.languagecoast.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Hardware-backed storage for cloud API keys and access tokens (Android KeyStore AES-GCM on Android,
 * Keychain with WhenUnlockedThisDeviceOnly on iOS). Secrets are never written into Preferences DataStore
 * or included in backups.
 */
interface SecretStore {
    /** Returns the secret stored under [key], or null if unset or unreadable. */
    fun get(key: String): String?

    /** Encrypts and stores [value] under [key]. */
    fun put(key: String, value: String)

    /** Deletes the secret stored under [key]. */
    fun remove(key: String)

    /** Monotonic counter incremented on every [put] or [remove] so Flow consumers re-emit. */
    val version: StateFlow<Long>
}

/** In-memory [SecretStore] used by unit tests. */
class InMemorySecretStore : SecretStore {
    private val store = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _version = MutableStateFlow(0L)
    override val version: StateFlow<Long> = _version.asStateFlow()

    override fun get(key: String): String? = store.value[key]

    override fun put(key: String, value: String) {
        store.update { it + (key to value) }
        _version.update { it + 1L }
    }

    override fun remove(key: String) {
        store.update { it - key }
        _version.update { it + 1L }
    }
}
