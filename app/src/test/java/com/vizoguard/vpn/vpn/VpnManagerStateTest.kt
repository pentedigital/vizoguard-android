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
        VpnTunnelService.serviceState.value = VpnState.IDLE
        VpnTunnelService.serviceError.value = null
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
        VpnTunnelService.serviceState.value = VpnState.IDLE

        // Should map to LICENSED since we weren't in IDLE
        assertEquals(VpnState.LICENSED, manager.status.value.state)
    }

    @Test
    fun `collect propagates error message from serviceError`() = runTest {
        val manager = VpnManager(mockContext(), scope)

        VpnTunnelService.serviceError.value = "Connection failed after 5 attempts"
        VpnTunnelService.serviceState.value = VpnState.ERROR

        assertEquals(VpnState.ERROR, manager.status.value.state)
        assertEquals("Connection failed after 5 attempts", manager.status.value.errorMessage)
    }

    @Test
    fun `startVpn sets CONNECTING state`() {
        val manager = VpnManager(mockContext(), scope)
        // startVpn now takes a JSON config string directly
        val configJson = """{"log":{"level":"warn"},"outbounds":[{"type":"shadowsocks","tag":"proxy","server":"1.2.3.4","server_port":8388}]}"""
        manager.startVpn(configJson)
        assertEquals(VpnState.CONNECTING, manager.status.value.state)
    }

    @Test
    fun `service IDLE after CONNECTED maps to LICENSED`() {
        val manager = VpnManager(mockContext(), scope)
        // Simulate service going through CONNECTED then back to IDLE
        VpnTunnelService.serviceState.value = VpnState.CONNECTED
        assertEquals(VpnState.CONNECTED, manager.status.value.state)
        VpnTunnelService.serviceState.value = VpnState.IDLE
        // Collector should map IDLE → LICENSED since we were CONNECTED
        assertEquals(VpnState.LICENSED, manager.status.value.state)
    }

    /** Returns a mock Context that silently no-ops on startService/startForegroundService */
    private fun mockContext(): android.content.Context {
        return io.mockk.mockk(relaxed = true)
    }
}
