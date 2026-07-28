package com.splitcruiser.app.data.firebase

import platform.Foundation.NSUserDefaults

/**
 * Session storage for iOS.
 *
 * The refresh token is a long-lived credential and `NSUserDefaults` is a plaintext plist inside the
 * app container, so it belongs in the Keychain — see [KeychainStore], which is what the app
 * actually uses. This implementation is kept for the non-sensitive values and for simulator work.
 */
class UserDefaultsStore : KeyValueStore {

    private val defaults = NSUserDefaults.standardUserDefaults

    override fun getString(key: String): String? = defaults.stringForKey(key)

    override fun putString(key: String, value: String) {
        defaults.setObject(value, key)
    }

    override fun remove(key: String) {
        defaults.removeObjectForKey(key)
    }
}
