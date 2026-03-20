package com.vizoguard.vpn.license

import com.vizoguard.vpn.api.ApiClient
import com.vizoguard.vpn.api.ApiException
import com.vizoguard.vpn.util.VizoLogger
import java.time.Instant
import java.time.temporal.ChronoUnit

class LicenseManager(
    private val store: SecureStore,
    private val api: ApiClient,
    private val deviceId: String
) {
    data class LicenseState(
        val key: String?,
        val status: String?,
        val expires: String?,
        val vpnAccessUrl: String?,
        val isValid: Boolean
    )

    fun getCachedState(): LicenseState {
        val key = store.getLicenseKey()
        val status = store.getLicenseStatus()
        val expires = store.getLicenseExpiry()
        val vpnUrl = store.getVpnAccessUrl()
        val isValid = key != null && status == "active" && expires != null && !isExpired(expires)
        return LicenseState(key, status, expires, vpnUrl, isValid)
    }

    suspend fun activate(key: String): Result<LicenseState> {
        val licenseResult = api.activateLicense(key, deviceId)
        if (licenseResult.isFailure) return Result.failure(licenseResult.exceptionOrNull()!!)

        val license = licenseResult.getOrThrow()
        store.saveLicenseKey(key)
        store.saveLicenseStatus(license.status)
        store.saveLicenseExpiry(license.expires)
        store.clearFirstFailureTimestamp()

        // Provision VPN key
        val vpnResult = api.createVpnKey(key, deviceId)
        if (vpnResult.isSuccess) {
            store.saveVpnAccessUrl(vpnResult.getOrThrow().accessUrl)
        }

        val state = getCachedState()
        VizoLogger.licenseEvent("activate: ${state.status}")
        return Result.success(state)
    }

    suspend fun validate(): Result<LicenseState> {
        val key = store.getLicenseKey() ?: return Result.failure(Exception("No license"))
        val result = api.activateLicense(key, deviceId)
        if (result.isSuccess) {
            val license = result.getOrThrow()
            store.saveLicenseStatus(license.status)
            store.saveLicenseExpiry(license.expires)
            store.clearFirstFailureTimestamp()
        } else {
            if (store.getFirstFailureTimestamp() == null) {
                store.saveFirstFailureTimestamp(System.currentTimeMillis())
            }
        }
        val state = getCachedState()
        VizoLogger.licenseEvent("validate: ${state.status}")
        return Result.success(state)
    }

    fun canConnectOffline(): Boolean {
        val state = getCachedState()
        if (state.vpnAccessUrl == null) return false
        val expires = state.expires ?: return false
        if (!isExpired(expires)) return true
        val firstFail = store.getFirstFailureTimestamp() ?: return false
        return isWithinGracePeriod(firstFail)
    }

    fun signOut() {
        store.clearAll()
    }

    companion object {
        private const val GRACE_PERIOD_DAYS = 7L
        private val KEY_PATTERN = Regex("^VIZO-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$")

        fun isExpired(iso8601: String): Boolean {
            return try {
                Instant.parse(iso8601).isBefore(Instant.now())
            } catch (e: Exception) { true }
        }

        fun isWithinGracePeriod(firstFailureMs: Long): Boolean {
            val deadline = Instant.ofEpochMilli(firstFailureMs).plus(GRACE_PERIOD_DAYS, ChronoUnit.DAYS)
            return Instant.now().isBefore(deadline)
        }

        fun maskKey(key: String): String {
            val parts = key.split("-")
            if (parts.size != 5) return key
            return "${parts[0]}-\u2022\u2022\u2022\u2022-\u2022\u2022\u2022\u2022-\u2022\u2022\u2022\u2022-${parts[4]}"
        }

        fun isValidKeyFormat(key: String): Boolean = KEY_PATTERN.matches(key)
    }
}
