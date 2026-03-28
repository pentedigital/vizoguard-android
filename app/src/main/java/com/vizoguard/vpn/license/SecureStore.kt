package com.vizoguard.vpn.license

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStore internal constructor(private val prefs: SharedPreferences) {

    fun saveLicenseKey(key: String) = prefs.edit().putString(KEY_LICENSE, key).apply()
    fun getLicenseKey(): String? = prefs.getString(KEY_LICENSE, null)

    fun saveVpnAccessUrl(url: String) = prefs.edit().putString(KEY_VPN_URL, url).apply()
    fun getVpnAccessUrl(): String? = prefs.getString(KEY_VPN_URL, null)
    fun clearVpnAccessUrl() = prefs.edit().remove(KEY_VPN_URL).apply()

    fun saveLicenseExpiry(iso8601: String) = prefs.edit().putString(KEY_EXPIRY, iso8601).apply()
    fun getLicenseExpiry(): String? = prefs.getString(KEY_EXPIRY, null)

    fun saveLicenseStatus(status: String) = prefs.edit().putString(KEY_STATUS, status).apply()
    fun getLicenseStatus(): String? = prefs.getString(KEY_STATUS, null)

    /** Atomic write of all license fields — prevents partial state on process kill */
    fun saveLicenseData(key: String, status: String, expiry: String) {
        prefs.edit()
            .putString(KEY_LICENSE, key)
            .putString(KEY_STATUS, status)
            .putString(KEY_EXPIRY, expiry)
            .apply()
    }

    /** Atomic write of status + expiry — for validation updates without changing key */
    fun saveValidation(status: String, expiry: String?) {
        val editor = prefs.edit().putString(KEY_STATUS, status)
        if (expiry != null) editor.putString(KEY_EXPIRY, expiry)
        editor.apply()
    }

    fun saveFirstFailureTimestamp(ts: Long) = prefs.edit().putLong(KEY_FIRST_FAIL, ts).apply()
    fun getFirstFailureTimestamp(): Long? {
        return if (prefs.contains(KEY_FIRST_FAIL)) prefs.getLong(KEY_FIRST_FAIL, 0) else null
    }
    fun clearFirstFailureTimestamp() = prefs.edit().remove(KEY_FIRST_FAIL).apply()

    fun saveDeviceId(id: String) = prefs.edit().putString(KEY_DEVICE_ID, id).apply()
    fun getDeviceId(): String? = prefs.getString(KEY_DEVICE_ID, null)

    fun saveAutoConnect(enabled: Boolean) = prefs.edit().putBoolean(KEY_AUTO_CONNECT, enabled).apply()
    fun getAutoConnect(): Boolean = prefs.getBoolean(KEY_AUTO_CONNECT, true)

    fun saveKillSwitch(enabled: Boolean) = prefs.edit().putBoolean(KEY_KILL_SWITCH, enabled).apply()
    fun getKillSwitch(): Boolean = prefs.getBoolean(KEY_KILL_SWITCH, true)

    fun saveNotifications(enabled: Boolean) = prefs.edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply()
    fun getNotifications(): Boolean = prefs.getBoolean(KEY_NOTIFICATIONS, false)

    fun saveVlessUuid(uuid: String) = prefs.edit().putString(KEY_VLESS_UUID, uuid).apply()
    fun getVlessUuid(): String? = prefs.getString(KEY_VLESS_UUID, null)
    fun clearVlessUuid() = prefs.edit().remove(KEY_VLESS_UUID).apply()

    fun saveTransportCache(networkKey: String, mode: String) {
        prefs.edit().putString("transport_cache_$networkKey", mode)
            .putLong("transport_cache_ts_$networkKey", System.currentTimeMillis())
            .apply()
    }

    fun getTransportCache(networkKey: String): String? {
        val ts = prefs.getLong("transport_cache_ts_$networkKey", 0)
        if (System.currentTimeMillis() - ts > 7 * 24 * 60 * 60 * 1000L) return null
        return prefs.getString("transport_cache_$networkKey", null)
    }

    /** Clears license data but preserves device ID and user settings */
    fun clearLicenseData() {
        prefs.edit()
            .remove(KEY_LICENSE)
            .remove(KEY_VPN_URL)
            .remove(KEY_EXPIRY)
            .remove(KEY_STATUS)
            .remove(KEY_FIRST_FAIL)
            .remove(KEY_VLESS_UUID)
            .apply()
    }

    companion object {
        private const val KEY_LICENSE = "license_key"
        private const val KEY_VPN_URL = "vpn_access_url"
        private const val KEY_EXPIRY = "license_expiry"
        private const val KEY_STATUS = "license_status"
        private const val KEY_FIRST_FAIL = "first_failure_ts"
        private const val KEY_DEVICE_ID = "device_uuid"
        private const val KEY_AUTO_CONNECT = "auto_connect"
        private const val KEY_KILL_SWITCH = "kill_switch"
        private const val KEY_NOTIFICATIONS = "notifications"
        private const val KEY_VLESS_UUID = "vless_uuid"

        fun create(context: Context): SecureStore {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context,
                "vizoguard_secure",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            return SecureStore(prefs)
        }
    }
}
