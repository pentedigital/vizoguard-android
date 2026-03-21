package com.vizoguard.vpn.vpn

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VpnManagerStateTest {

    private lateinit var scope: TestScope

    @Before
    fun setup() {
        scope = TestScope(UnconfinedTestDispatcher())
        // Reset static service state
        ShadowsocksService.serviceState.value = VpnState.IDLE
        ShadowsocksService.serviceError.value = null
    }

    @Test
    fun `updateState transitions from IDLE to LICENSED`() {
        val manager = VpnManager(mockContext(), scope)
        manager.updateState(VpnState.LICENSED)
        assertEquals(VpnState.LICENSED, manager.status.value.state)
    }

    @Test
    fun `updateState sets connectedSince on CONNECTED`() {
        val manager = VpnManager(mockContext(), scope)
        manager.updateState(VpnState.CONNECTED)
        assertNotNull(manager.status.value.connectedSince)
    }

    @Test
    fun `updateState clears connectedSince on LICENSED`() {
        val manager = VpnManager(mockContext(), scope)
        manager.updateState(VpnState.CONNECTED)
        assertNotNull(manager.status.value.connectedSince)
        manager.updateState(VpnState.LICENSED)
        assertNull(manager.status.value.connectedSince)
    }

    @Test
    fun `updateState preserves connectedSince during RECONNECTING`() {
        val manager = VpnManager(mockContext(), scope)
        manager.updateState(VpnState.CONNECTED)
        val since = manager.status.value.connectedSince
        manager.updateState(VpnState.RECONNECTING)
        assertEquals(since, manager.status.value.connectedSince)
    }

    @Test
    fun `updateState sets error message on ERROR`() {
        val manager = VpnManager(mockContext(), scope)
        manager.updateState(VpnState.ERROR, "Server unreachable")
        assertEquals("Server unreachable", manager.status.value.errorMessage)
    }

    @Test
    fun `collect maps IDLE from service to LICENSED when not in IDLE`() = runTest {
        val manager = VpnManager(mockContext(), scope)
        manager.updateState(VpnState.LICENSED)

        // Simulate service emitting IDLE (e.g., from onDestroy)
        ShadowsocksService.serviceState.value = VpnState.IDLE

        // Should map to LICENSED since we weren't in IDLE
        assertEquals(VpnState.LICENSED, manager.status.value.state)
    }

    @Test
    fun `collect propagates error message from serviceError`() = runTest {
        val manager = VpnManager(mockContext(), scope)

        ShadowsocksService.serviceError.value = "Connection failed after 5 attempts"
        ShadowsocksService.serviceState.value = VpnState.ERROR

        assertEquals(VpnState.ERROR, manager.status.value.state)
        assertEquals("Connection failed after 5 attempts", manager.status.value.errorMessage)
    }

    @Test
    fun `startVpn sets CONNECTING with server info`() {
        val manager = VpnManager(mockContext(), scope)
        // This will try to start the service (no-op in unit test due to mocked context)
        // but we can check the status was set before the intent
        val url = "ss://Y2hhY2hhMjAtaWV0Zi1wb2x5MTMwNTpwYXNzd29yZA==@1.2.3.4:8388/?outline=1"
        manager.startVpn(url, true)
        assertEquals(VpnState.CONNECTING, manager.status.value.state)
        assertEquals("1.2.3.4", manager.status.value.serverHost)
        assertEquals("chacha20-ietf-poly1305", manager.status.value.encryptionMethod)
    }

    @Test
    fun `startVpn sets ERROR for invalid URL`() {
        val manager = VpnManager(mockContext(), scope)
        manager.startVpn("invalid-url", true)
        assertEquals(VpnState.ERROR, manager.status.value.state)
        assertEquals("Invalid VPN configuration", manager.status.value.errorMessage)
    }

    @Test
    fun `service IDLE after CONNECTED maps to LICENSED`() {
        val manager = VpnManager(mockContext(), scope)
        // Simulate service going through CONNECTED then back to IDLE
        ShadowsocksService.serviceState.value = VpnState.CONNECTED
        assertEquals(VpnState.CONNECTED, manager.status.value.state)
        ShadowsocksService.serviceState.value = VpnState.IDLE
        // Collector should map IDLE → LICENSED since we were CONNECTED
        assertEquals(VpnState.LICENSED, manager.status.value.state)
    }

    /** Returns a mock Context that silently no-ops on startService/startForegroundService */
    private fun mockContext(): android.content.Context {
        return io.mockk.mockk(relaxed = true)
    }
}
