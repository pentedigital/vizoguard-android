package com.vizoguard.vpn.license

import android.content.Context
import java.util.UUID

object DeviceId {
    fun get(context: Context): String {
        val store = SecureStore.create(context)
        val existing = store.getDeviceId()
        if (existing != null) return existing
        val uuid = UUID.randomUUID().toString()
        store.saveDeviceId(uuid)
        return uuid
    }
}
