package com.vizoguard.vpn

import com.vizoguard.vpn.vpn.VpnState
import org.junit.Assert.*
import org.junit.Test

class AppStateTest {
    @Test
    fun `initial screen is ACTIVATE when no license`() {
        assertEquals(Screen.ACTIVATE, AppState.screenForState(VpnState.IDLE, hasLicense = false))
    }

    @Test
    fun `screen is MAIN when licensed`() {
        assertEquals(Screen.MAIN, AppState.screenForState(VpnState.LICENSED, hasLicense = true))
    }

    @Test
    fun `screen is MAIN when connected`() {
        assertEquals(Screen.MAIN, AppState.screenForState(VpnState.CONNECTED, hasLicense = true))
    }

    @Test
    fun `planDisplayName maps vpn to Basic`() {
        assertEquals("Basic", AppState.planDisplayName("vpn"))
    }

    @Test
    fun `planDisplayName maps security_vpn to Pro`() {
        assertEquals("Pro", AppState.planDisplayName("security_vpn"))
    }
}
