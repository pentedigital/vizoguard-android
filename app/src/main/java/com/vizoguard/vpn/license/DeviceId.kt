package com.vizoguard.vpn.license

import java.util.UUID

object DeviceId {
    fun get(store: SecureStore): String {
        val existing = store.getDeviceId()
        if (existing != null) return existing
        val uuid = UUID.randomUUID().toString()
        store.saveDeviceId(uuid)
        return uuid
    }
}
