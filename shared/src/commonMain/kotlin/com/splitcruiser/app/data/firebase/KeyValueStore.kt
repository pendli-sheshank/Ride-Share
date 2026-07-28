package com.splitcruiser.app.data.firebase

/**
 * Somewhere to keep the signed-in session across launches.
 *
 * An interface rather than an `expect class` because the two platforms need different constructor
 * arguments — Android needs a `Context`, iOS needs nothing — and `expect class` cannot vary its
 * constructor. Each platform's implementation is passed in from the app entry point, which also
 * makes the whole token-refresh path testable on Linux with [InMemoryStore].
 */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

/** For tests, and for a platform that has no persistence wired up yet. */
class InMemoryStore : KeyValueStore {
    private val values = mutableMapOf<String, String>()
    override fun getString(key: String): String? = values[key]
    override fun putString(key: String, value: String) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
}
