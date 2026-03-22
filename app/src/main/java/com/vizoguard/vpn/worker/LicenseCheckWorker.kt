package com.vizoguard.vpn.worker

import android.content.Context
import android.content.Intent
import androidx.work.*
import com.vizoguard.vpn.api.ApiClient
import com.vizoguard.vpn.license.DeviceId
import com.vizoguard.vpn.license.LicenseManager
import com.vizoguard.vpn.license.SecureStore
import com.vizoguard.vpn.util.VizoLogger
import com.vizoguard.vpn.vpn.ShadowsocksService
import com.vizoguard.vpn.vpn.VpnManager
import java.util.concurrent.TimeUnit

class LicenseCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = SecureStore.create(applicationContext)
        val api = ApiClient()
        try {
            val deviceId = DeviceId.get(store)
            val manager = LicenseManager(store, api, deviceId)
            val result = manager.validate()

            // On network failure, check grace period BEFORE cached expiry
            if (result.isFailure) {
                val cachedExpiry = store.getLicenseExpiry()
                val isExpired = cachedExpiry != null && LicenseManager.isExpired(cachedExpiry)

                if (isExpired) {
                    // Both network failed AND cached license expired — stop VPN
                    VizoLogger.systemEvent("License expired (cached) during network error — stopping VPN")
                    val stopIntent = Intent(applicationContext, ShadowsocksService::class.java).apply {
                        action = VpnManager.ACTION_DISCONNECT
                    }
                    applicationContext.startService(stopIntent)
                    return Result.success()
                }

                // Network failed but cached license still valid (grace period) — retry later
                VizoLogger.systemEvent("Network error but cached license still valid — will retry")
                return Result.retry()
            }

            // Validation succeeded — if license is no longer valid, stop the VPN
            val state = manager.getCachedState()
            if (!state.isValid) {
                VizoLogger.systemEvent("License invalid — stopping VPN")
                val stopIntent = Intent(applicationContext, ShadowsocksService::class.java).apply {
                    action = VpnManager.ACTION_DISCONNECT
                }
                applicationContext.startService(stopIntent)
            }
        } finally {
            api.close()
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "license_check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LicenseCheckWorker>(24, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
