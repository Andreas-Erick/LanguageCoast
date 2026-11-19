package com.andreaserick.languagecoast.data

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Stores API keys and access tokens in the iOS Keychain with [kSecAttrAccessibleWhenUnlockedThisDeviceOnly]
 * so secrets stay hardware-protected on this device and never sync to iCloud or unencrypted backups.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosSecretStore : SecretStore {

    private val _version = MutableStateFlow(0L)
    override val version: StateFlow<Long> = _version.asStateFlow()

    override fun get(key: String): String? = memScoped {
        val query = baseQuery(key)
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        CFRelease(query)
        if (status != errSecSuccess) {
            if (status != errSecItemNotFound) {
                logError(TAG, "Keychain read failed for '$key' (status $status)")
            }
            return null
        }
        val data = CFBridgingRelease(result.value) as? NSData ?: return null
        NSString.create(data = data, encoding = NSUTF8StringEncoding) as? String
    }

    override fun put(key: String, value: String) {
        val nsString = NSString.create(string = value)
        val data = nsString.dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val dataRef = CFBridgingRetain(data)
        val query = baseQuery(key)
        CFDictionaryAddValue(query, kSecValueData, dataRef)
        CFDictionaryAddValue(query, kSecAttrAccessible, kSecAttrAccessibleWhenUnlockedThisDeviceOnly)

        val addStatus = SecItemAdd(query, null)
        CFRelease(query)
        if (addStatus == errSecDuplicateItem) {
            val matchQuery = baseQuery(key)
            val update = CFDictionaryCreateMutable(null, 2, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
            CFDictionaryAddValue(update, kSecValueData, dataRef)
            CFDictionaryAddValue(update, kSecAttrAccessible, kSecAttrAccessibleWhenUnlockedThisDeviceOnly)
            val updateStatus = SecItemUpdate(matchQuery, update)
            CFRelease(matchQuery)
            CFRelease(update)
            if (updateStatus != errSecSuccess) {
                logError(TAG, "Keychain update failed for '$key' (status $updateStatus)")
            }
        } else if (addStatus != errSecSuccess) {
            logError(TAG, "Keychain write failed for '$key' (status $addStatus)")
        }
        if (dataRef != null) CFRelease(dataRef)
        _version.update { it + 1L }
    }

    override fun remove(key: String) {
        val query = baseQuery(key)
        val status = SecItemDelete(query)
        CFRelease(query)
        if (status != errSecSuccess && status != errSecItemNotFound) {
            logError(TAG, "Keychain delete failed for '$key' (status $status)")
        }
        _version.update { it + 1L }
    }

    private fun baseQuery(key: String): CFDictionaryRef {
        val dict = CFDictionaryCreateMutable(null, 5, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)!!
        val serviceRef = CFBridgingRetain(SERVICE_NAME)
        val accountRef = CFBridgingRetain(key)
        CFDictionaryAddValue(dict, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(dict, kSecAttrService, serviceRef)
        CFDictionaryAddValue(dict, kSecAttrAccount, accountRef)
        if (serviceRef != null) CFRelease(serviceRef)
        if (accountRef != null) CFRelease(accountRef)
        return dict
    }

    private companion object {
        const val SERVICE_NAME = "com.andreaserick.languagecoast.secrets"
        const val TAG = "IosSecretStore"
    }
}
