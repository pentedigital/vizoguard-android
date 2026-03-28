package com.vizoguard.vpn.vpn

import org.json.JSONArray
import org.json.JSONObject

object ConfigBuilder {

    private const val VLESS_SERVER = "vizoguard.com"
    private const val VLESS_PORT = 443
    private const val WS_PATH = "/ws"
    private const val TUN_ADDRESS = "172.19.0.1/30"

    private val PRIVATE_CIDRS = listOf(
        "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16",
        "127.0.0.0/8", "169.254.0.0/16"
    )

    fun buildShadowsocks(host: String, port: Int, method: String, password: String): String {
        return JSONObject().apply {
            put("log", JSONObject().put("level", "warn"))
            put("inbounds", JSONArray().put(buildTunInbound()))
            put("outbounds", JSONArray()
                .put(JSONObject().apply {
                    put("type", "shadowsocks")
                    put("tag", "proxy")
                    put("server", host)
                    put("server_port", port)
                    put("method", method)
                    put("password", password)
                })
                .put(JSONObject().put("type", "direct").put("tag", "direct"))
                .put(JSONObject().put("type", "block").put("tag", "block"))
            )
            put("route", JSONObject().apply {
                put("auto_detect_interface", true)
                put("final", "proxy")
                put("rules", JSONArray().put(buildPrivateNetworkRule()))
            })
        }.toString()
    }

    fun buildShadowsocks(config: ShadowsocksConfig): String {
        return buildShadowsocks(config.host, config.port, config.method, config.password)
    }

    fun buildVless(uuid: String, serverIp: String): String {
        return JSONObject().apply {
            put("log", JSONObject().put("level", "warn"))
            put("inbounds", JSONArray().put(buildTunInbound()))
            put("outbounds", JSONArray()
                .put(JSONObject().apply {
                    put("type", "vless")
                    put("tag", "proxy")
                    put("server", VLESS_SERVER)
                    put("server_port", VLESS_PORT)
                    put("uuid", uuid)
                    put("tls", JSONObject().apply {
                        put("enabled", true)
                        put("server_name", VLESS_SERVER)
                    })
                    put("transport", JSONObject().apply {
                        put("type", "ws")
                        put("path", WS_PATH)
                    })
                })
                .put(JSONObject().put("type", "direct").put("tag", "direct"))
                .put(JSONObject().put("type", "block").put("tag", "block"))
            )
            put("dns", JSONObject().put("servers", JSONArray()
                .put(JSONObject().apply {
                    put("address", "https://1.1.1.1/dns-query")
                    put("tag", "dns-remote")
                    put("strategy", "ipv4_only")
                })
                .put(JSONObject().apply {
                    put("address", "https://9.9.9.9/dns-query")
                    put("tag", "dns-fallback")
                    put("strategy", "ipv4_only")
                })
            ))
            put("route", JSONObject().apply {
                put("auto_detect_interface", true)
                put("final", "proxy")
                put("rules", JSONArray()
                    .put(JSONObject().apply {
                        put("ip_cidr", JSONArray().put("$serverIp/32"))
                        put("outbound", "direct")
                    })
                    .put(buildPrivateNetworkRule())
                )
            })
        }.toString()
    }

    private fun buildTunInbound(): JSONObject {
        return JSONObject().apply {
            put("type", "tun")
            put("auto_route", true)
            put("strict_route", true)
            put("inet4_address", TUN_ADDRESS)
            put("sniff", true)
            put("sniff_override_destination", false)
        }
    }

    private fun buildPrivateNetworkRule(): JSONObject {
        return JSONObject().apply {
            put("ip_cidr", JSONArray().apply { PRIVATE_CIDRS.forEach { put(it) } })
            put("outbound", "direct")
        }
    }
}
