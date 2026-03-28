package com.vizoguard.vpn.vpn

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ConfigBuilderTest {

    @Test
    fun buildShadowsocks_containsCorrectOutbound() {
        val json = ConfigBuilder.buildShadowsocks("1.2.3.4", 8388, "chacha20-ietf-poly1305", "secret")
        val config = JSONObject(json)
        val outbound = config.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("shadowsocks", outbound.getString("type"))
        assertEquals("1.2.3.4", outbound.getString("server"))
        assertEquals(8388, outbound.getInt("server_port"))
        assertEquals("chacha20-ietf-poly1305", outbound.getString("method"))
        assertEquals("secret", outbound.getString("password"))
    }

    @Test
    fun buildShadowsocks_hasTunInbound() {
        val json = ConfigBuilder.buildShadowsocks("1.2.3.4", 8388, "aes-256-gcm", "pass")
        val config = JSONObject(json)
        val inbound = config.getJSONArray("inbounds").getJSONObject(0)
        assertEquals("tun", inbound.getString("type"))
        assertTrue(inbound.getBoolean("auto_route"))
    }

    @Test
    fun buildShadowsocks_hasPrivateNetworkBypass() {
        val json = ConfigBuilder.buildShadowsocks("1.2.3.4", 8388, "aes-256-gcm", "pass")
        val config = JSONObject(json)
        val rules = config.getJSONObject("route").getJSONArray("rules")
        assertTrue(rules.length() > 0)
        val cidrs = rules.getJSONObject(0).getJSONArray("ip_cidr")
        val cidrList = (0 until cidrs.length()).map { cidrs.getString(it) }
        assertTrue(cidrList.contains("10.0.0.0/8"))
        assertTrue(cidrList.contains("127.0.0.0/8"))
    }

    @Test
    fun buildVless_containsCorrectOutbound() {
        val json = ConfigBuilder.buildVless("test-uuid-1234", "93.184.216.34")
        val config = JSONObject(json)
        val outbound = config.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("vless", outbound.getString("type"))
        assertEquals("vizoguard.com", outbound.getString("server"))
        assertEquals(443, outbound.getInt("server_port"))
        assertEquals("test-uuid-1234", outbound.getString("uuid"))
    }

    @Test
    fun buildVless_hasTlsAndWsTransport() {
        val json = ConfigBuilder.buildVless("uuid", "1.2.3.4")
        val config = JSONObject(json)
        val outbound = config.getJSONArray("outbounds").getJSONObject(0)
        assertTrue(outbound.getJSONObject("tls").getBoolean("enabled"))
        assertEquals("ws", outbound.getJSONObject("transport").getString("type"))
        assertEquals("/ws", outbound.getJSONObject("transport").getString("path"))
    }

    @Test
    fun buildVless_hasServerIpBypass() {
        val json = ConfigBuilder.buildVless("uuid", "93.184.216.34")
        val config = JSONObject(json)
        val rules = config.getJSONObject("route").getJSONArray("rules")
        val firstRule = rules.getJSONObject(0)
        val cidrs = firstRule.getJSONArray("ip_cidr")
        assertEquals("93.184.216.34/32", cidrs.getString(0))
    }

    @Test
    fun buildVless_hasDnsServers() {
        val json = ConfigBuilder.buildVless("uuid", "1.2.3.4")
        val config = JSONObject(json)
        val servers = config.getJSONObject("dns").getJSONArray("servers")
        assertTrue(servers.length() >= 1)
    }

    @Test
    fun buildFromShadowsocksConfig_delegatesCorrectly() {
        val ssConfig = ShadowsocksConfig("host", 1234, "aes-256-gcm", "pw")
        val json = ConfigBuilder.buildShadowsocks(ssConfig)
        val config = JSONObject(json)
        val outbound = config.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("host", outbound.getString("server"))
        assertEquals(1234, outbound.getInt("server_port"))
    }
}
