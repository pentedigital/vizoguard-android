package com.vizoguard.vpn.worker

import android.content.Context
import androidx.work.*
import com.vizoguard.vpn.api.ApiClient
import com.vizoguard.vpn.license.DeviceId
import com.vizoguard.vpn.license.LicenseManager
import com.vizoguard.vpn.license.SecureStore
import java.util.concurrent.TimeUnit

class LicenseCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = SecureStore.create(applicationContext)
        val api = ApiClient()
        val deviceId = DeviceId.get(applicationContext)
        val manager = LicenseManager(store, api, deviceId)
        manager.validate()
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
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
