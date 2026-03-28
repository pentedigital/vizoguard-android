package com.vizoguard.vpn.vpn

import com.vizoguard.vpn.ui.ConnectionMode
import org.junit.Assert.*
import org.junit.Test

class ConnectionManagerTest {

    @Test
    fun privacyMode_mapsToAuto() {
        assertEquals(TransportMode.AUTO, TransportMode.fromConnectionMode(ConnectionMode.PRIVACY))
    }

    @Test
    fun streamingMode_mapsToDirect() {
        assertEquals(TransportMode.DIRECT, TransportMode.fromConnectionMode(ConnectionMode.STREAMING))
    }

    @Test
    fun workMode_mapsToAuto() {
        assertEquals(TransportMode.AUTO, TransportMode.fromConnectionMode(ConnectionMode.WORK))
    }
}
