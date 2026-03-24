package com.vizoguard.vpn.license

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for the atomic saveValidation() method and SecureStore data integrity.
 * Verifies that status + expiry are written in a single atomic operation.
 */
class SecureStoreValidationTest {

    @Test
    fun `saveValidation writes status and expiry atomically`() {
        val store = SecureStore(InMemoryPrefs())
        store.saveLicenseKey("VIZO-AAAA-BBBB-CCCC-DDDD")

        store.saveValidation("active", "2027-06-01T00:00:00Z")

        assertEquals("active", store.getLicenseStatus())
        assertEquals("2027-06-01T00:00:00Z", store.getLicenseExpiry())
        // Key should be untouched
        assertEquals("VIZO-AAAA-BBBB-CCCC-DDDD", store.getLicenseKey())
    }

    @Test
    fun `saveValidation with null expiry preserves existing expiry`() {
        val store = SecureStore(InMemoryPrefs())
        store.saveLicenseExpiry("2027-01-01T00:00:00Z")
        store.saveLicenseStatus("active")

        // Server sends null expiry — should not overwrite
        store.saveValidation("cancelled", null)

        assertEquals("cancelled", store.getLicenseStatus())
        assertEquals("2027-01-01T00:00:00Z", store.getLicenseExpiry())
    }

    @Test
    fun `saveValidation overwrites previous status`() {
        val store = SecureStore(InMemoryPrefs())
        store.saveValidation("active", "2027-06-01T00:00:00Z")
        store.saveValidation("suspended", "2027-06-01T00:00:00Z")

        assertEquals("suspended", store.getLicenseStatus())
    }

    @Test
    fun `saveValidation does not affect VPN URL or device ID`() {
        val store = SecureStore(InMemoryPrefs())
        store.saveVpnAccessUrl("ss://test@1.2.3.4:8388")
        store.saveDeviceId("device-12345678")
        store.saveLicenseKey("VIZO-AAAA-BBBB-CCCC-DDDD")

        store.saveValidation("expired", "2025-01-01T00:00:00Z")

        assertEquals("ss://test@1.2.3.4:8388", store.getVpnAccessUrl())
        assertEquals("device-12345678", store.getDeviceId())
        assertEquals("VIZO-AAAA-BBBB-CCCC-DDDD", store.getLicenseKey())
    }

    @Test
    fun `saveLicenseData and saveValidation are consistent`() {
        val store = SecureStore(InMemoryPrefs())

        // saveLicenseData sets key + status + expiry
        store.saveLicenseData("VIZO-AAAA-BBBB-CCCC-DDDD", "active", "2027-06-01T00:00:00Z")
        assertEquals("active", store.getLicenseStatus())
        assertEquals("2027-06-01T00:00:00Z", store.getLicenseExpiry())

        // saveValidation updates status + expiry without touching key
        store.saveValidation("cancelled", "2027-06-01T00:00:00Z")
        assertEquals("cancelled", store.getLicenseStatus())
        assertEquals("VIZO-AAAA-BBBB-CCCC-DDDD", store.getLicenseKey())
    }

    @Test
    fun `saveValidation with all status transitions`() {
        val store = SecureStore(InMemoryPrefs())
        val expiry = "2027-06-01T00:00:00Z"

        for (status in listOf("active", "cancelled", "suspended", "expired")) {
            store.saveValidation(status, expiry)
            assertEquals(status, store.getLicenseStatus())
        }
    }
}
