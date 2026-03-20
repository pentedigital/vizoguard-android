package com.vizoguard.vpn.license

import android.content.SharedPreferences

class InMemoryPrefs : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()

    override fun getString(key: String?, defValue: String?) = data[key] as? String ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean) = data[key] as? Boolean ?: defValue
    override fun getLong(key: String?, defValue: Long) = data[key] as? Long ?: defValue
    override fun getInt(key: String?, defValue: Int) = data[key] as? Int ?: defValue
    override fun getFloat(key: String?, defValue: Float) = data[key] as? Float ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?) = defValues
    override fun contains(key: String?) = data.containsKey(key)
    override fun getAll(): MutableMap<String, *> = data.toMutableMap()
    override fun edit() = InMemoryEditor()
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    inner class InMemoryEditor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private var doClear = false
        private val removals = mutableSetOf<String>()

        override fun putString(key: String?, value: String?): SharedPreferences.Editor { pending[key!!] = value; return this }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { pending[key!!] = value; return this }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor { pending[key!!] = value; return this }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor { pending[key!!] = value; return this }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { pending[key!!] = value; return this }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor { pending[key!!] = values; return this }
        override fun remove(key: String?): SharedPreferences.Editor { removals.add(key!!); return this }
        override fun clear(): SharedPreferences.Editor { doClear = true; return this }
        override fun commit(): Boolean { applyChanges(); return true }
        override fun apply() { applyChanges() }

        private fun applyChanges() {
            if (doClear) data.clear()
            removals.forEach { data.remove(it) }
            data.putAll(pending)
        }
    }
}
