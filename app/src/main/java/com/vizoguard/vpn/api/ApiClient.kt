package com.vizoguard.vpn.api

import com.vizoguard.vpn.util.VizoLogger
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable data class LicenseResponse(val valid: Boolean, val status: String, val expires: String)
@Serializable data class VpnResponse(@SerialName("access_url") val accessUrl: String)
@Serializable data class ErrorResponse(val error: String, val status: String = "")
@Serializable data class HealthResponse(val status: String)
@Serializable data class LicenseRequest(val key: String, @SerialName("device_id") val deviceId: String)

class ApiClient(private val baseUrl: String = "https://vizoguard.com/api") {

    private val client = HttpClient(Android) {
        engine { connectTimeout = 15_000; socketTimeout = 15_000 }
    }

    suspend fun activateLicense(key: String, deviceId: String): Result<LicenseResponse> {
        return try {
            val response = client.post("$baseUrl/license") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(LicenseRequest(key, deviceId)))
            }
            VizoLogger.apiCall("/license", response.status.isSuccess(), response.status.value)
            if (response.status.isSuccess()) {
                Result.success(parseLicenseResponse(response.bodyAsText()))
            } else {
                val err = parseErrorResponse(response.bodyAsText())
                Result.failure(ApiException(response.status.value, err.error, err.status))
            }
        } catch (e: Exception) {
            VizoLogger.apiCall("/license", false)
            Result.failure(e)
        }
    }

    suspend fun createVpnKey(key: String, deviceId: String): Result<VpnResponse> {
        return try {
            val response = client.post("$baseUrl/vpn/create") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(LicenseRequest(key, deviceId)))
            }
            VizoLogger.apiCall("/vpn/create", response.status.isSuccess(), response.status.value)
            if (response.status.isSuccess()) {
                Result.success(parseVpnResponse(response.bodyAsText()))
            } else {
                val err = parseErrorResponse(response.bodyAsText())
                Result.failure(ApiException(response.status.value, err.error, err.status))
            }
        } catch (e: Exception) {
            VizoLogger.apiCall("/vpn/create", false)
            Result.failure(e)
        }
    }

    suspend fun getVpnKey(key: String, deviceId: String): Result<VpnResponse> {
        return try {
            val response = client.post("$baseUrl/vpn/get") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(LicenseRequest(key, deviceId)))
            }
            VizoLogger.apiCall("/vpn/get", response.status.isSuccess(), response.status.value)
            if (response.status.isSuccess()) {
                Result.success(parseVpnResponse(response.bodyAsText()))
            } else {
                val err = parseErrorResponse(response.bodyAsText())
                Result.failure(ApiException(response.status.value, err.error, err.status))
            }
        } catch (e: Exception) {
            VizoLogger.apiCall("/vpn/get", false)
            Result.failure(e)
        }
    }

    suspend fun checkHealth(): Result<HealthResponse> {
        return try {
            val response = client.get("$baseUrl/health")
            VizoLogger.apiCall("/health", response.status.isSuccess(), response.status.value)
            Result.success(parseHealthResponse(response.bodyAsText()))
        } catch (e: Exception) {
            VizoLogger.apiCall("/health", false)
            Result.failure(e)
        }
    }

    suspend fun checkVpnStatus(): Result<HealthResponse> {
        return try {
            val response = client.get("$baseUrl/vpn/status")
            VizoLogger.apiCall("/vpn/status", response.status.isSuccess(), response.status.value)
            Result.success(parseHealthResponse(response.bodyAsText()))
        } catch (e: Exception) {
            VizoLogger.apiCall("/vpn/status", false)
            Result.failure(e)
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parseLicenseResponse(body: String): LicenseResponse = json.decodeFromString(body)
        fun parseVpnResponse(body: String): VpnResponse = json.decodeFromString(body)
        fun parseErrorResponse(body: String): ErrorResponse = json.decodeFromString(body)
        fun parseHealthResponse(body: String): HealthResponse = json.decodeFromString(body)
    }
}

class ApiException(val httpStatus: Int, override val message: String, val status: String) : Exception(message)
