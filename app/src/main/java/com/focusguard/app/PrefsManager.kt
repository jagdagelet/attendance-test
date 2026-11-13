package com.focusguard.app

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("focus_guard_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_BLOCKED_APPS = "blocked_apps"
        private const val KEY_IS_CLOCKED_IN = "is_clocked_in"
        private const val KEY_CLOCK_IN_TIME = "clock_in_time"
        private const val KEY_FIRST_RUN = "is_first_run"
    }

    var isFirstRun: Boolean
        get() = prefs.getBoolean(KEY_FIRST_RUN, true)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_RUN, value).apply()

    var blockedApps: Set<String>
        get() = prefs.getStringSet(KEY_BLOCKED_APPS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_BLOCKED_APPS, value).apply()

    var isClockedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_CLOCKED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_CLOCKED_IN, value).apply()

    var clockInTime: Long
        get() = prefs.getLong(KEY_CLOCK_IN_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_CLOCK_IN_TIME, value).apply()

    fun addBlockedApp(packageName: String) {
        val current = blockedApps.toMutableSet()
        current.add(packageName)
        blockedApps = current
    }

    fun removeBlockedApp(packageName: String) {
        val current = blockedApps.toMutableSet()
        current.remove(packageName)
        blockedApps = current
    }

    fun isAppBlocked(packageName: String): Boolean {
        return blockedApps.contains(packageName)
    }
}
