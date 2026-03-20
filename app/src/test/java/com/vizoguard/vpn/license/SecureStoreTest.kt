package com.vizoguard.vpn.license

import org.junit.Assert.*
import org.junit.Test

private fun createTestStore() = SecureStore(InMemoryPrefs())

class SecureStoreTest {
    @Test
    fun `store and retrieve license key`() {
        val store = createTestStore()
        store.saveLicenseKey("VIZO-AAAA-BBBB-CCCC-DDDD")
        assertEquals("VIZO-AAAA-BBBB-CCCC-DDDD", store.getLicenseKey())
    }

    @Test
    fun `returns null when no key stored`() {
        val store = createTestStore()
        assertNull(store.getLicenseKey())
    }

    @Test
    fun `store and retrieve VPN access URL`() {
        val store = createTestStore()
        store.saveVpnAccessUrl("ss://base64@host:port")
        assertEquals("ss://base64@host:port", store.getVpnAccessUrl())
    }

    @Test
    fun `clear all removes everything`() {
        val store = createTestStore()
        store.saveLicenseKey("VIZO-AAAA-BBBB-CCCC-DDDD")
        store.saveVpnAccessUrl("ss://test")
        store.clearAll()
        assertNull(store.getLicenseKey())
        assertNull(store.getVpnAccessUrl())
    }

    @Test
    fun `store and retrieve license expiry`() {
        val store = createTestStore()
        store.saveLicenseExpiry("2027-03-20T00:00:00Z")
        assertEquals("2027-03-20T00:00:00Z", store.getLicenseExpiry())
    }

    @Test
    fun `store and retrieve first failure timestamp`() {
        val store = createTestStore()
        assertNull(store.getFirstFailureTimestamp())
        store.saveFirstFailureTimestamp(1711929600000L)
        assertEquals(1711929600000L, store.getFirstFailureTimestamp())
        store.clearFirstFailureTimestamp()
        assertNull(store.getFirstFailureTimestamp())
    }

    @Test
    fun `store and retrieve device ID`() {
        val store = createTestStore()
        assertNull(store.getDeviceId())
        store.saveDeviceId("test-uuid-1234")
        assertEquals("test-uuid-1234", store.getDeviceId())
    }

    @Test
    fun `settings have correct defaults`() {
        val store = createTestStore()
        assertTrue(store.getAutoConnect()) // default ON
        assertTrue(store.getKillSwitch()) // default ON
        assertFalse(store.getNotifications()) // default OFF
    }
}
