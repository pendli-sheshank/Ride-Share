package com.splitcruiser.app.data.firebase

import platform.Foundation.NSUserDefaults

/**
 * Plain `NSUserDefaults` session storage for iOS.
 *
 * `NSUserDefaults` is a plaintext plist inside the app container and is copied into unencrypted
 * device backups, so it must NOT be used for the session — the refresh token is a long-lived
 * credential. [KeychainStore] is the secure implementation and is what the app wires in
 * (`ViewModel.swift`). This class is retained only for non-sensitive values and simulator
 * experiments; do not point the repository at it.
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
