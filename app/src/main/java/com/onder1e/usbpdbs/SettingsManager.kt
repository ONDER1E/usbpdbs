package com.onder1e.usbpdbs

import android.content.Context
import android.content.SharedPreferences

object SettingsManager {
    private const val PREFS_NAME = "tbp_settings"
    private const val KEY_ENABLE_THRESHOLD = "enable_threshold"
    private const val KEY_DISABLE_THRESHOLD = "disable_threshold"
    private const val KEY_MIN_STATE_SECONDS = "min_state_seconds"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var enableThreshold: Int
        get() = prefs.getInt(KEY_ENABLE_THRESHOLD, 42)
        set(value) = prefs.edit().putInt(KEY_ENABLE_THRESHOLD, value).apply()

    var disableThreshold: Int
        get() = prefs.getInt(KEY_DISABLE_THRESHOLD, 38)
        set(value) = prefs.edit().putInt(KEY_DISABLE_THRESHOLD, value).apply()

    var minStateSeconds: Int
        get() = prefs.getInt(KEY_MIN_STATE_SECONDS, 60)
        set(value) = prefs.edit().putInt(KEY_MIN_STATE_SECONDS, value).apply()
}
