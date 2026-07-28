package com.splitcruiser.app.data.firebase

import com.splitcruiser.app.data.nowMs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.concurrent.Volatile

@Serializable
data class StoredSession(
    val uid: String,
    val email: String,
    val idToken: String,
    val refreshToken: String,
    val expiresAtMs: Long,
)

/**
 * Holds the signed-in session and keeps the ID token fresh.
 *
 * Firebase ID tokens last an hour; the refresh token does not expire. Persisting both is what lets
 * the app restore a session on a cold start — something it has never done, since the old repository
 * kept the current user in memory only and dropped it on every launch.
 *
 * The refresh is single-flight: six polling streams all noticing an expired token at once would
 * otherwise fire six refresh calls that race each other.
 */
internal class TokenProvider(
    private val store: KeyValueStore,
    private val json: Json,
    /** Supplied by [FirebaseAuthClient] so this class does not depend on it. */
    private val refresh: suspend (refreshToken: String) -> StoredSession,
) {
    private val mutex = Mutex()

    @Volatile
    private var session: StoredSession? = null

    val uid: String? get() = session?.uid

    val current: StoredSession? get() = session

    /** Reads any session left by a previous launch. Call once, at construction. */
    fun restore(): StoredSession? {
        val raw = store.getString(KEY_SESSION) ?: return null
        val restored = runCatching { json.decodeFromString<StoredSession>(raw) }.getOrNull()
        if (restored == null) store.remove(KEY_SESSION)
        session = restored
        return restored
    }

    fun set(newSession: StoredSession) {
        session = newSession
        store.putString(KEY_SESSION, json.encodeToString(StoredSession.serializer(), newSession))
    }

    fun clear() {
        session = null
        store.remove(KEY_SESSION)
    }

    /** A valid ID token, refreshing first if it is within [SKEW_MS] of expiry. Null when signed out. */
    suspend fun idToken(): String? {
        val existing = session ?: return null
        if (nowMs() < existing.expiresAtMs - SKEW_MS) return existing.idToken
        return forceRefresh(existing.idToken)
    }

    /**
     * Refreshes even though the token has not expired by the clock. Used after a 401, where the
     * token looked valid but the server disagreed — so expiry is exactly the wrong thing to check.
     *
     * Callers pass the token that failed. If the stored token has since changed, another caller
     * already refreshed and that result is returned instead of issuing a second call; that is what
     * keeps six polling streams from firing six refreshes and racing each other's token rotation.
     */
    suspend fun forceRefresh(staleToken: String?): String? = mutex.withLock {
        val existing = session ?: return null
        if (staleToken != null && existing.idToken != staleToken) return existing.idToken
        val refreshed = refresh(existing.refreshToken)
        // The refresh response carries no email, so keep the one already known.
        val merged = refreshed.copy(email = existing.email.ifBlank { refreshed.email })
        set(merged)
        merged.idToken
    }

    private companion object {
        const val KEY_SESSION = "firebase_session"

        /** Refresh a minute early so a request never races the expiry. */
        const val SKEW_MS = 60_000L
    }
}
