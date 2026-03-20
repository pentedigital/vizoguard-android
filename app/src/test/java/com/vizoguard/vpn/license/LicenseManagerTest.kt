package com.vizoguard.vpn.license

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class LicenseManagerTest {
    @Test
    fun `isExpired returns false for future date`() {
        val future = Instant.now().plus(30, ChronoUnit.DAYS).toString()
        assertFalse(LicenseManager.isExpired(future))
    }

    @Test
    fun `isExpired returns true for past date`() {
        val past = Instant.now().minus(1, ChronoUnit.DAYS).toString()
        assertTrue(LicenseManager.isExpired(past))
    }

    @Test
    fun `isWithinGracePeriod returns true within 7 days of first failure`() {
        val firstFail = Instant.now().minus(3, ChronoUnit.DAYS).toEpochMilli()
        assertTrue(LicenseManager.isWithinGracePeriod(firstFail))
    }

    @Test
    fun `isWithinGracePeriod returns false after 7 days`() {
        val firstFail = Instant.now().minus(8, ChronoUnit.DAYS).toEpochMilli()
        assertFalse(LicenseManager.isWithinGracePeriod(firstFail))
    }

    @Test
    fun `formatKeyForDisplay masks middle segments`() {
        assertEquals("VIZO-\u2022\u2022\u2022\u2022-\u2022\u2022\u2022\u2022-\u2022\u2022\u2022\u2022-DDDD", LicenseManager.maskKey("VIZO-AAAA-BBBB-CCCC-DDDD"))
    }

    @Test
    fun `validateKeyFormat accepts valid key`() {
        assertTrue(LicenseManager.isValidKeyFormat("VIZO-AAAA-BBBB-CCCC-DDDD"))
    }

    @Test
    fun `validateKeyFormat rejects invalid key`() {
        assertFalse(LicenseManager.isValidKeyFormat("invalid"))
        assertFalse(LicenseManager.isValidKeyFormat("VIZO-AAA-BBB-CCC-DDD"))
    }
}
