@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package ng.heynaija.app.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFRetain
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSDictionary
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dictionaryWithObjects
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

fun iosPlatform(debug: Boolean) = Platform(
    secure = KeychainStore("ng.heynaija.app"),
    settings = DefaultsStore(),
    debug = debug,
)

private class DefaultsStore : KeyValueStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    override fun get(key: String): String? = defaults.stringForKey(key)
    override fun set(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, key)
    }
}

/** Generic passwords in the Keychain, for this device only and readable after the first unlock. */
private class KeychainStore(private val service: String) : KeyValueStore {
    // A Security constant as an Objective-C object, for an NSDictionary.
    private fun cf(ref: CFTypeRef?): Any = CFBridgingRelease(CFRetain(ref))!!

    private fun query(key: String, vararg extra: Pair<Any, Any>): CFDictionaryRef? {
        val pairs = listOf(cf(kSecClass) to cf(kSecClassGenericPassword), cf(kSecAttrService) to service, cf(kSecAttrAccount) to key) + extra
        val dictionary = NSDictionary.dictionaryWithObjects(pairs.map { it.second }, pairs.map { it.first })
        return CFBridgingRetain(dictionary)?.reinterpret()
    }

    override fun get(key: String): String? = memScoped {
        val query = query(key, cf(kSecReturnData) to cf(kCFBooleanTrue), cf(kSecMatchLimit) to cf(kSecMatchLimitOne))
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        CFRelease(query)
        if (status != errSecSuccess) return null
        val data = CFBridgingRelease(result.value) as? NSData ?: return null
        data.toByteArray().decodeToString()
    }

    override fun set(key: String, value: String?) {
        val existing = query(key)
        SecItemDelete(existing)
        CFRelease(existing)
        if (value == null) return
        val add = query(
            key,
            cf(kSecValueData) to value.encodeToByteArray().toNSData(),
            cf(kSecAttrAccessible) to cf(kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly),
        )
        SecItemAdd(add, null)
        CFRelease(add)
    }
}

internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply { usePinned { memcpy(it.addressOf(0), bytes, length) } }
}

internal fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
