package com.vizoguard.vpn.license

import com.vizoguard.vpn.api.ApiClient
import com.vizoguard.vpn.api.ApiException
import com.vizoguard.vpn.api.LicenseResponse
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Tests for validate() correctness — atomic state updates,
 * VPN URL clearing on status changes, grace period behavior.
 */
class LicenseManagerValidateTest {

    private lateinit var store: SecureStore
    private lateinit var api: ApiClient
    private lateinit var manager: LicenseManager

    @Before
    fun setup() {
        store = SecureStore(InMemoryPrefs())
        api = mockk(relaxed = true)
        manager = LicenseManager(store, api, "test-device-id")
    }

    private fun future(days: Long = 30) = Instant.now().plus(days, ChronoUnit.DAYS).toString()
    private fun past(days: Long = 1) = Instant.now().minus(days, ChronoUnit.DAYS).toString()

    // ── Atomic state update tests ───────────────────────────────────────────

    @Test
    fun `validate writes status and expiry atomically via saveValidation`() = runTest {
        store.saveLicenseKey("VIZO-AAAA-BBBB-CCCC-DDDD")
        store.saveLicenseStatus("active")
        val newExpiry = future(365)

        coEvery { api.activateLicense(any(), any()) } returns Result.success(
            LicenseResponse(valid = true, status = "cancelled", expires = newExpiry)
        )

        manager.validate()

        // Both should be updated (atomically via saveValidation)
        assertEquals("cancelled", store.getLicenseStatus())
        assertEquals(newExpiry, store.getLicenseExpiry())
    }

    @Test
    fun `validate with null expiry preserves existing expiry`() = runTest {
        store.saveLicenseKey("VIZO-AAAA-BBBB-CCCC-DDDD")
        store.saveLicenseExpiry("2027-01-01T00:00:00Z")

        coEvery { api.activateLicense(any(), any()) } returns Result.success(
            LicenseResponse(valid = true, status = "active", expires = null)
        )

        manager.validate()

        assertEquals("active", store.getLicenseStatus())
        assertEquals("2027-01-01T00:00:00Z", store.getLicenseExpiry())
    }

    // ── VPN URL clearing on status transitions ──────────────────────────────

    @Test
    fun `validate clears VPN URL when suspended license becomes active`() = runTest {
        store.saveLicenseKey("VIZO-AAAA-BBBB-CCCC-DDDD")
        store.saveLicenseStatus("suspended")
        store.saveVpnAccessUrl("ss://stale@old:1234")

        coEvery { api.activateLicense(any(), any()) } returns Result.success(
            LicenseResponse(valid = true, status = "active", expires = future())
        )

        manager.validate()

        // VPN URL cleared so fresh key gets provisioned
        assertNull(store.getVpnAccessUrl())
        assertEquals("active", store.getLicenseStatus())
    }

    @Test
    fun `validate clears VPN URL on expired status`() = runTest {
        store.saveLicenseKey("VIZO-AAAA-BBBB-CCCC-DDDD")
        store.saveLicenseStatus("active")
        store.saveVpnAccessUrl("ss://shouldgo@1.2.3.4:8388")

        coEvery { api.activateLicense(any(), any()) } returns Result.success(
            LicenseResponse(valid = true, status = "expired", expires = past())
        )

        manager.validate()

        assertNull(store.getVpnAccessUrl())
    }

    @Test
    fun `validate clears VPN URL on suspended status`() = runTest {
        store.saveLicenseKey("VIZO-AAAA-BBBB-CCCC-DDDD")
        store.saveLicenseStatus("active")
        store.saveVpnAccessUrl("ss://shouldgo@1.2.3.4:8388")

        coEvery { api.activateLicense(any(), any()) } returns Result.success(
            LicenseResponse(valid = true, status = "suspended", expires = future())
        )

        manager.validate()

        assertNull(store.getVpnAccessUrl())
    }

    @Test
    fun `validate does NOT clear VPN URL when active stays active`() = runTest {
        store.saveLicenseKey("VIZO-AAAA-BBBB-CCCC-DDDD")
        store.saveLicenseStatus("active")
        store.saveVpnAccessUrl("ss://keep@1.2.3.4:8388")

        coEvery { api.activateLicense(any(), any()) } returns Result.success(
            LicenseResponse(valid = true, status = "active", expires = future())
        )

        manager.validate()

        assertEquals("ss://keep@1.2.3.4:8388", store.getVpnAccessUrl())
    }

    // ── 403 server rejection tests ──────────────────────────────────────────

    @Test
    fun `validate on 403 suspended clears VPN URL without starting grace`() = runTest {
        store.saveLicenseKey("VIZO-AAAA-BBBB-CCCC-DDDD")
        store.saveLicenseStatus("active")
        store.saveVpnAccessUrl("ss://revoke@1.2.3.4:8388")

        coEvery { api.activateLicense(any(), any()) } returns Result.failure(
            ApiException(403, "Suspended", "suspended")
        )

        manager.validate()

        assertEquals("suspended", store.getLicenseStatus())
        assertNull(store.getVpnAccessUrl())
        // Grace period should NOT start on 403
        assertNull(store.getFirstFailureTimestamp())
    }

    @Test
    fun `validate on 403 expired clears VPN URL without starting grace`() = runTest {
        store.saveLicenseKey("VIZO-AAAA-BBBB-CCCC-DDDD")
        store.saveLicenseStatus("active")
        store.saveVpnAccessUrl("ss://revoke@1.2.3.4:8388")

        coEvery { api.activateLicense(any(), any()) } returns Result.failure(
            ApiException(403, "Expired", "expired")
        )

        manager.validate()

        assertEquals("expired", store.getLicenseStatus())
        assertNull(store.getVpnAccessUrl())
        assertNull(store.getFirstFailureTimestamp())
    }

    // ── Network error grace period tests ────────────────────────────────────

    @Test
    fun `validate on network error starts grace period only once`() = runTest {
        store.saveLicenseKey("VIZO-AAAA-BBBB-CCCC-DDDD")

        coEvery { api.activateLicense(any(), any()) } returns Result.failure(
            Exception("Network unreachable")
        )

        manager.validate()
        val firstTs = store.getFirstFailureTimestamp()
        assertNotNull(firstTs)

        // Second failure should not overwrite timestamp
        Thread.sleep(10) // ensure time passes
        manager.validate()
        assertEquals(firstTs, store.getFirstFailureTimestamp())
    }

    @Test
    fun `validate success after network error clears grace period`() = runTest {
        store.saveLicenseKey("VIZO-AAAA-BBBB-CCCC-DDDD")
        store.saveFirstFailureTimestamp(System.currentTimeMillis() - 86400000) // 1 day ago

        coEvery { api.activateLicense(any(), any()) } returns Result.success(
            LicenseResponse(valid = true, status = "active", expires = future())
        )

        manager.validate()

        assertNull(store.getFirstFailureTimestamp())
    }

    // ── Edge cases ──────────────────────────────────────────────────────────

    @Test
    fun `validate with no stored license key returns failure`() = runTest {
        // No key stored
        val result = manager.validate()
        assertTrue(result.isFailure)
    }

    @Test
    fun `getCachedState reflects validation changes`() = runTest {
        store.saveLicenseKey("VIZO-AAAA-BBBB-CCCC-DDDD")
        store.saveValidation("active", future())
        store.saveVpnAccessUrl("ss://test@1.2.3.4:8388")

        val state = manager.getCachedState()
        assertTrue(state.isValid)
        assertEquals("active", state.status)
        assertEquals("ss://test@1.2.3.4:8388", state.vpnAccessUrl)
    }

    @Test
    fun `getCachedState marks expired license as invalid`() {
        store.saveLicenseKey("VIZO-AAAA-BBBB-CCCC-DDDD")
        store.saveValidation("active", past())

        val state = manager.getCachedState()
        assertFalse(state.isValid)
    }
}
