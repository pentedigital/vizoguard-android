package com.vizoguard.vpn

import android.app.Application
import com.vizoguard.vpn.worker.LicenseCheckWorker

class VizoguardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LicenseCheckWorker.schedule(this)
    }
}
