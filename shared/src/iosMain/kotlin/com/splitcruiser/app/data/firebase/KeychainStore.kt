package com.splitcruiser.app.data.firebase

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
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

/**
 * Keychain-backed session storage for iOS.
 *
 * The session includes the Firebase refresh token — a long-lived credential — so it belongs in the
 * Keychain, not [UserDefaultsStore]'s plaintext plist (which is copied into unencrypted iTunes/Finder
 * backups and readable by anything with container access). Items are written with
 * `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`: available to the app after the first unlock
 * following a boot, never migrated to another device, and never included in a backup.
 *
 * NOTE: Kotlin/Native cannot be compiled on Linux, so this file is verified only by the macOS iOS CI
 * (the simulator build in `build-ios.yml` and the archive in `ios-release.yml`). It follows the
 * CFDictionary cinterop pattern used by well-tested KMP keychain wrappers.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class KeychainStore(
    private val service: String = "com.splitcruiser.app.session",
) : KeyValueStore {

    override fun getString(key: String): String? = memScoped {
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(
            cfDictionaryOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to CFBridgingRetain(service),
                kSecAttrAccount to CFBridgingRetain(key),
                kSecReturnData to kCFBooleanTrue,
                kSecMatchLimit to kSecMatchLimitOne,
            ),
            result.ptr,
        )
        if (status != errSecSuccess) return@memScoped null
        val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
        NSString.create(data, NSUTF8StringEncoding) as String?
    }

    override fun putString(key: String, value: String) {
        // Delete-then-add keeps this idempotent regardless of whether an item already exists.
        remove(key)
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        SecItemAdd(
            cfDictionaryOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to CFBridgingRetain(service),
                kSecAttrAccount to CFBridgingRetain(key),
                kSecValueData to CFBridgingRetain(data),
                kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            ),
            null,
        )
    }

    override fun remove(key: String) {
        SecItemDelete(
            cfDictionaryOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to CFBridgingRetain(service),
                kSecAttrAccount to CFBridgingRetain(key),
            ),
        )
    }

    /**
     * Builds a `CFDictionary` for a Security query. Values produced by [CFBridgingRetain] are +1
     * retained; they are owned by the dictionary for the duration of the synchronous Security call
     * and released when it is freed by the runtime, which is the lifetime this class needs.
     */
    private fun cfDictionaryOf(vararg pairs: Pair<CFStringRef?, COpaquePointer?>): CFDictionaryRef? {
        val dictionary = CFDictionaryCreateMutable(kCFAllocatorDefault, pairs.size.toLong(), null, null)
        pairs.forEach { (k, v) -> CFDictionaryAddValue(dictionary, k, v) }
        return dictionary
    }
}
