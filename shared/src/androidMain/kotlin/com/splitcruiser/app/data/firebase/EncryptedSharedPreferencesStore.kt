package com.splitcruiser.app.data.firebase

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Keystore-backed session storage for Android.
 *
 * The session includes the Firebase refresh token — a long-lived credential — so it is encrypted at
 * rest under a key held in the Android Keystore (hardware-backed where the device supports it), via
 * Jetpack Security's [EncryptedSharedPreferences]. The bytes on disk are ciphertext and the key
 * never leaves the Keystore, so a backup, a device transfer, or another app reading the file sees
 * only ciphertext. It replaces [SharedPreferencesStore], which stored the token as plaintext XML.
 *
 * Two robustness details:
 *  - A **separate file** (`split_cruiser_secure_session`) is used, and the legacy plaintext file is
 *    deleted on first construction — [EncryptedSharedPreferences.create] cannot open a file that
 *    already holds unencrypted data, and leaving the old token in plaintext would defeat the point.
 *    A user is signed out once across the upgrade, which is a fair trade for not stranding a
 *    plaintext credential on disk.
 *  - If Keystore initialisation throws — a rare but real failure mode on devices with a damaged
 *    keystore — it falls back to a plain `MODE_PRIVATE` file rather than crashing sign-in. That
 *    fallback loses the encryption layer but is still app-private and excluded from backup
 *    (see the app's `data_extraction_rules.xml`), so the credential still cannot leave the device.
 */
class EncryptedSharedPreferencesStore(
    context: Context,
    name: String = SECURE_PREFS,
) : KeyValueStore {

    private val prefs: SharedPreferences = createPrefs(context.applicationContext, name)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    private companion object {
        const val SECURE_PREFS = "split_cruiser_secure_session"
        const val LEGACY_PLAINTEXT_PREFS = "split_cruiser_session"

        fun createPrefs(context: Context, name: String): SharedPreferences {
            // Best-effort removal of any pre-existing plaintext session from an older build.
            runCatching { context.deleteSharedPreferences(LEGACY_PLAINTEXT_PREFS) }

            return runCatching {
                val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                EncryptedSharedPreferences.create(
                    name,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }.getOrElse {
                context.getSharedPreferences(name, Context.MODE_PRIVATE)
            }
        }
    }
}
