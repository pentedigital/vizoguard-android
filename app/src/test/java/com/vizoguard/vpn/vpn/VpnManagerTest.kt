package com.vizoguard.vpn.vpn

import org.junit.Assert.*
import org.junit.Test

class VpnManagerTest {
    @Test
    fun `parseShadowsocksUrl extracts host port method password`() {
        // chacha20-ietf-poly1305:password base64-encoded
        val url = "ss://Y2hhY2hhMjAtaWV0Zi1wb2x5MTMwNTpwYXNzd29yZA==@1.2.3.4:8388/?outline=1"
        val config = VpnManager.parseShadowsocksUrl(url)
        assertNotNull(config)
        assertEquals("1.2.3.4", config!!.host)
        assertEquals(8388, config.port)
        assertEquals("chacha20-ietf-poly1305", config.method)
        assertEquals("password", config.password)
    }

    @Test
    fun `parseShadowsocksUrl returns null for invalid URL`() {
        assertNull(VpnManager.parseShadowsocksUrl("https://not-a-ss-url"))
        assertNull(VpnManager.parseShadowsocksUrl(""))
    }

    @Test
    fun `parseShadowsocksUrl returns null for missing at sign`() {
        assertNull(VpnManager.parseShadowsocksUrl("ss://Y2hhY2hhMjA="))
    }

    @Test
    fun `VpnState enum has all required states`() {
        val states = VpnState.entries
        assertTrue(states.contains(VpnState.IDLE))
        assertTrue(states.contains(VpnState.LICENSED))
        assertTrue(states.contains(VpnState.CONNECTING))
        assertTrue(states.contains(VpnState.CONNECTED))
        assertTrue(states.contains(VpnState.RECONNECTING))
        assertTrue(states.contains(VpnState.BLOCKED))
        assertTrue(states.contains(VpnState.ERROR))
        assertEquals(7, states.size)
    }
}
