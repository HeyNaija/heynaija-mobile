@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package ng.heynaija.app.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.NSData
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecMissingEntitlement
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessGroup
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

fun iosPlatform(debug: Boolean, gistAudio: GistAudioFactory?, ageRange: AppleAgeRange?) = Platform(
    secure = KeychainStore("ng.heynaija.app"),
    settings = DefaultsStore(),
    debug = debug,
    gistAudio = gistAudio,
    push = ApnsPushDevice(KeychainStore("ng.heynaija.app.shared", accessGroup = APP_GROUP)),
    ageSignals = ageRange?.let(::DeclaredAgeSignals),
)

/**
 * The app group's defaults, which the notification service extension reads too:
 * it shows a notification only for the member signed in (`me`, AppState.kt).
 * Without the app group (a build with no team) they are the app's own.
 */
private class DefaultsStore : KeyValueStore {
    private val defaults = NSUserDefaults(suiteName = APP_GROUP)
    override fun get(key: String): String? = defaults.stringForKey(key)
    override fun set(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, key)
    }
}

/**
 * Generic passwords in the Keychain, for this device only and readable after the
 * first unlock. The queries are built from Core Foundation values directly:
 * bridging Kotlin strings through an NSDictionary made the Keychain answer
 * errSecParam. With [accessGroup], new items go in that group, for the app's
 * extensions to read; a build without the group keeps them in the app's own.
 * Reading looks in every group the app has.
 */
private class KeychainStore(private val service: String, private val accessGroup: String? = null) : KeyValueStore {
    private fun cfString(value: String): CFStringRef? = CFStringCreateWithCString(null, value, kCFStringEncodingUTF8)

    private fun <T> query(key: String, extra: (CFMutableDictionaryRef?) -> Unit = {}, use: (CFMutableDictionaryRef?) -> T): T {
        val dictionary = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        val service = cfString(service)
        val account = cfString(key)
        CFDictionaryAddValue(dictionary, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(dictionary, kSecAttrService, service)
        CFDictionaryAddValue(dictionary, kSecAttrAccount, account)
        extra(dictionary)
        try {
            return use(dictionary)
        } finally {
            CFRelease(dictionary)
            CFRelease(service)
            CFRelease(account)
        }
    }

    override fun get(key: String): String? = memScoped {
        val result = alloc<CFTypeRefVar>()
        val status = query(key, extra = {
            CFDictionaryAddValue(it, kSecReturnData, kCFBooleanTrue)
            CFDictionaryAddValue(it, kSecMatchLimit, kSecMatchLimitOne)
        }) { SecItemCopyMatching(it, result.ptr) }
        if (status != errSecSuccess) {
            if (status != errSecItemNotFound) println("HeyNaija: the Keychain would not read $key ($status)")
            return null
        }
        val data: CFDataRef = result.value?.reinterpret() ?: return null
        try {
            val length = CFDataGetLength(data).toInt()
            CFDataGetBytePtr(data)?.readBytes(length)?.decodeToString()
        } finally {
            CFRelease(data)
        }
    }

    override fun set(key: String, value: String?) {
        query(key) { SecItemDelete(it) }
        if (value == null) return
        val bytes = value.encodeToByteArray()
        val data = bytes.usePinned { CFDataCreate(null, it.addressOf(0).reinterpret(), bytes.size.toLong()) }
        val group = accessGroup?.let(::cfString)
        try {
            fun add(inGroup: Boolean) = query(key, extra = {
                CFDictionaryAddValue(it, kSecValueData, data)
                CFDictionaryAddValue(it, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
                if (inGroup && group != null) CFDictionaryAddValue(it, kSecAttrAccessGroup, group)
            }) { SecItemAdd(it, null) }
            var status = add(inGroup = true)
            if (status == errSecMissingEntitlement && group != null) status = add(inGroup = false)
            if (status != errSecSuccess) println("HeyNaija: the Keychain refused to keep $key ($status)")
        } finally {
            CFRelease(data)
            group?.let(::CFRelease)
        }
    }
}

internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply { usePinned { memcpy(it.addressOf(0), bytes, length) } }
}

internal fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
