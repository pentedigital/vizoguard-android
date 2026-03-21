package com.vizoguard.vpn.license

import com.vizoguard.vpn.api.ApiClient
import com.vizoguard.vpn.api.ApiException
import com.vizoguard.vpn.api.LicenseResponse
import com.vizoguard.vpn.api.VpnResponse
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class LicenseManagerIntegrationTest {

    private lateinit var store: SecureStore
    private lateinit var api: ApiClient
    private lateinit var manager: LicenseManager

    @Before
    fun setup() {
        store = SecureStore(InMemoryPrefs())
        api = mockk(relaxed = true)
        manager = LicenseManager(store, api, "test-device-id")
    }

    @Test
    fun `activate saves license and VPN key on success`() = runTest {
        val future = Instant.now().plus(30, ChronoUnit.DAYS).toString()
        coEvery { api.activateLicense(any(), any()) } returns Result.success(
            LicenseResponse(valid = true, status = "active", expires = future)
        )
        coEvery { api.createVpnKey(any(), any()) } returns Result.success(
            VpnResponse(accessUrl = "ss://test@1.2.3.4:8388")
        )

        val result = manager.activate("VIZO-AAAA-BBBB-CCCC-DDDD")
        assertTrue(result.isSuccess)
        assertEquals("active", store.getLicenseStatus())
        assertEquals("ss://test@1.2.3.4:8388", store.getVpnAccessUrl())
        assertTrue(result.getOrThrow().isValid)
    }

    @Test
    fun `activate returns failure when license API fails`() = runTest {
        coEvery { api.activateLicense(any(), any()) } returns Result.failure(
            ApiException(403, "Expired", "expired")
        )

        val result = manager.activate("VIZO-AAAA-BBBB-CCCC-DDDD")
        assertTrue(result.isFailure)
        assertNull(store.getLicenseKey())
    }

    @Test
    fun `activate falls back to getVpnKey when createVpnKey fails`() = runTest {
        val future = Instant.now().plus(30, ChronoUnit.DAYS).toString()
        coEvery { api.activateLicense(any(), any()) } returns Result.success(
            LicenseResponse(valid = true, status = "active", expires = future)
        )
        coEvery { api.createVpnKey(any(), any()) } returns Result.failure(Exception("create failed"))
        coEvery { api.getVpnKey(any(), any()) } returns Result.success(
            VpnResponse(accessUrl = "ss://fallback@1.2.3.4:8388")
        )

        val result = manager.activate("VIZO-AAAA-BBBB-CCCC-DDDD")
        assertTrue(result.isSuccess)
        assertEquals("ss://fallback@1.2.3.4:8388", store.getVpnAccessUrl())
    }

    @Test
    fun `activate succeeds without VPN key when both create and get fail`() = runTest {
        val future = Instant.now().plus(30, ChronoUnit.DAYS).toString()
        coEvery { api.activateLicense(any(), any()) } returns Result.success(
            LicenseResponse(valid = true, status = "active", expires = future)
        )
        coEvery { api.createVpnKey(any(), any()) } returns Result.failure(Exception("create failed"))
        coEvery { api.getVpnKey(any(), any()) } returns Result.failure(Exception("get failed"))

        val result = manager.activate("VIZO-AAAA-BBBB-CCCC-DDDD")
        assertTrue(result.isSuccess)
        assertNull(store.getVpnAccessUrl())
        assertEquals("active", store.getLicenseStatus())
    }

    @Test
    fun `validate records first failure timestamp on API failure`() = runTest {
        store.saveLicenseKey("VIZO-AAAA-BBBB-CCCC-DDDD")
        coEvery { api.activateLicense(any(), any()) } returns Result.failure(Exception("network error"))

        manager.validate()
        assertNotNull(store.getFirstFailureTimestamp())
    }

    @Test
    fun `validate clears failure timestamp on success`() = runTest {
        store.saveLicenseKey("VIZO-AAAA-BBBB-CCCC-DDDD")
        store.saveFirstFailureTimestamp(System.currentTimeMillis())
        val future = Instant.now().plus(30, ChronoUnit.DAYS).toString()
        coEvery { api.activateLicense(any(), any()) } returns Result.success(
            LicenseResponse(valid = true, status = "active", expires = future)
        )

        manager.validate()
        assertNull(store.getFirstFailureTimestamp())
    }

    @Test
    fun `canConnectOffline returns true within grace period`() {
        val future = Instant.now().minus(1, ChronoUnit.DAYS).toString() // expired
        store.saveLicenseKey("VIZO-AAAA-BBBB-CCCC-DDDD")
        store.saveLicenseExpiry(future)
        store.saveVpnAccessUrl("ss://test")
        store.saveFirstFailureTimestamp(Instant.now().minus(3, ChronoUnit.DAYS).toEpochMilli())

        assertTrue(manager.canConnectOffline())
    }

    @Test
    fun `canConnectOffline returns false after grace period`() {
        val future = Instant.now().minus(1, ChronoUnit.DAYS).toString()
        store.saveLicenseKey("VIZO-AAAA-BBBB-CCCC-DDDD")
        store.saveLicenseExpiry(future)
        store.saveVpnAccessUrl("ss://test")
        store.saveFirstFailureTimestamp(Instant.now().minus(10, ChronoUnit.DAYS).toEpochMilli())

        assertFalse(manager.canConnectOffline())
    }

    @Test
    fun `signOut clears license data but preserves device ID and settings`() {
        store.saveLicenseKey("VIZO-AAAA-BBBB-CCCC-DDDD")
        store.saveVpnAccessUrl("ss://test")
        store.saveDeviceId("device-123")
        store.saveAutoConnect(true)
        store.saveKillSwitch(false)

        manager.signOut()

        assertNull(store.getLicenseKey())
        assertNull(store.getVpnAccessUrl())
        assertEquals("device-123", store.getDeviceId())
        assertTrue(store.getAutoConnect())
        assertFalse(store.getKillSwitch())
    }
}
