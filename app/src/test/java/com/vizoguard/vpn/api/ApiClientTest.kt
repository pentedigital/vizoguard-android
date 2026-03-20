package com.vizoguard.vpn.api

import org.junit.Assert.*
import org.junit.Test

class ApiClientTest {
    @Test
    fun `parseLicenseResponse extracts valid fields`() {
        val json = """{"valid":true,"status":"active","expires":"2027-03-20T00:00:00Z"}"""
        val result = ApiClient.parseLicenseResponse(json)
        assertTrue(result.valid)
        assertEquals("active", result.status)
        assertEquals("2027-03-20T00:00:00Z", result.expires)
    }

    @Test
    fun `parseVpnResponse extracts access_url`() {
        val json = """{"access_url":"ss://Y2hhY2hhMjA=@1.2.3.4:8388/?outline=1"}"""
        val result = ApiClient.parseVpnResponse(json)
        assertEquals("ss://Y2hhY2hhMjA=@1.2.3.4:8388/?outline=1", result.accessUrl)
    }

    @Test
    fun `parseErrorResponse extracts status field`() {
        val json = """{"error":"License expired","status":"expired"}"""
        val result = ApiClient.parseErrorResponse(json)
        assertEquals("expired", result.status)
        assertEquals("License expired", result.error)
    }

    @Test
    fun `parseHealthResponse extracts status`() {
        val json = """{"status":"ok","timestamp":"2026-03-20T08:00:00Z"}"""
        val result = ApiClient.parseHealthResponse(json)
        assertEquals("ok", result.status)
    }
}
