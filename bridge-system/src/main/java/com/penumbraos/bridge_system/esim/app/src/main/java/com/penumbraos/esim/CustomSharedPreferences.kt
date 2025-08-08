package com.penumbraos.esim

import android.content.SharedPreferences
import android.util.Log

class Preferences {
    var EuiccAvailable = false
    var errorOnInstallation = false
    var LogicalChannels = HashSet<String>()
}

class CustomSharedPreferences : SharedPreferences {
    val preferences = Preferences()

    override fun getAll(): Map<String?, *>? {
        TODO("Not yet implemented in getAll")
    }

    override fun getString(key: String?, defValue: String?): String? {
        TODO("Not yet implemented in getString")
    }

    override fun getStringSet(
        key: String?,
        defValues: Set<String?>?
    ): Set<String?>? {
        if (key == "LogicalChannels") {
            return preferences.LogicalChannels
        }
        TODO("Not yet implemented in getStringSet")
    }

    override fun getInt(key: String?, defValue: Int): Int {
        TODO("Not yet implemented in getInt")
    }

    override fun getLong(key: String?, defValue: Long): Long {
        TODO("Not yet implemented in getLong")
    }

    override fun getFloat(key: String?, defValue: Float): Float {
        TODO("Not yet implemented in getFloat")
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        if (key == "EuiccAvailable") {
            return preferences.EuiccAvailable
        } else if (key == "errorOnInstallation") {
            return preferences.errorOnInstallation
        }

        TODO("Not yet implemented in getBoolean")
    }

    override fun contains(key: String?): Boolean {
        TODO("Not yet implemented in contains")
    }

    override fun edit(): SharedPreferences.Editor? {
        return CustomSharedPreferencesEditor(preferences)
    }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        TODO("Not yet implemented in registerOnSharedPreferenceChangeListener")
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        TODO("Not yet implemented in unregisterOnSharedPreferenceChangeListener")
    }
}

class CustomSharedPreferencesEditor(val preferences: Preferences) : SharedPreferences.Editor {
    override fun putString(
        key: String?,
        value: String?
    ): SharedPreferences.Editor? {
        Log.w("CustomSharedPreferencesEditor", "putString $key $value")
        return this
    }

    override fun putStringSet(
        key: String?,
        values: Set<String?>?
    ): SharedPreferences.Editor? {
        Log.w("CustomSharedPreferencesEditor", "putStringSet $key $values")
        if (key == "LogicalChannels") {
            preferences.LogicalChannels = values as HashSet<String>
        }
        return this
    }

    override fun putInt(
        key: String?,
        value: Int
    ): SharedPreferences.Editor? {
        Log.w("CustomSharedPreferencesEditor", "putInt $key $value")
        return this
    }

    override fun putLong(
        key: String?,
        value: Long
    ): SharedPreferences.Editor? {
        Log.w("CustomSharedPreferencesEditor", "putLong $key $value")
        return this
    }

    override fun putFloat(
        key: String?,
        value: Float
    ): SharedPreferences.Editor? {
        Log.w("CustomSharedPreferencesEditor", "putFloat $key $value")
        return this
    }

    override fun putBoolean(
        key: String?,
        value: Boolean
    ): SharedPreferences.Editor? {
        Log.w("CustomSharedPreferencesEditor", "putBoolean $key $value")
        if (key == "EuiccAvailable") {
            preferences.EuiccAvailable = value
        } else if (key == "errorOnInstallation") {
            preferences.errorOnInstallation = value
        }
        return this
    }

    override fun remove(key: String?): SharedPreferences.Editor? {
        Log.w("CustomSharedPreferencesEditor", "remove $key")
        return this
    }

    override fun clear(): SharedPreferences.Editor? {
        Log.w("CustomSharedPreferencesEditor", "clear")
        return this
    }

    override fun commit(): Boolean {
        Log.w("CustomSharedPreferencesEditor", "commit")
        return true
    }

    override fun apply() {
        Log.w("CustomSharedPreferencesEditor", "apply")
    }

}