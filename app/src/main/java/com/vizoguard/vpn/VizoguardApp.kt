package com.vizoguard.vpn

import android.app.Application
import com.vizoguard.vpn.util.VizoLogger
import com.vizoguard.vpn.worker.LicenseCheckWorker

class VizoguardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        VizoLogger.init(this)
        VizoLogger.systemEvent("App started")
        LicenseCheckWorker.schedule(this)
    }
}
