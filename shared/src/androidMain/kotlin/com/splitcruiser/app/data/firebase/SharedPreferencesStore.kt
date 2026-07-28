package com.splitcruiser.app.data.firebase

import android.content.Context

class SharedPreferencesStore(
    context: Context,
    name: String = "split_cruiser_session",
) : KeyValueStore {

    private val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}
