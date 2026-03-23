package com.vizoguard.vpn.api

import com.vizoguard.vpn.util.Tag
import com.vizoguard.vpn.util.VizoLogger
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

@Serializable data class LicenseResponse(val valid: Boolean, val status: String, val expires: String?)
@Serializable data class VpnResponse(@SerialName("access_url") val accessUrl: String)
@Serializable data class ErrorResponse(val error: String, val status: String = "")
@Serializable data class HealthResponse(val status: String)
@Serializable data class LicenseRequest(val key: String, @SerialName("device_id") val deviceId: String)

class ApiClient(private val baseUrl: String = "https://vizoguard.com/api") {

    private val client by lazy {
        HttpClient(Android) {
            engine { connectTimeout = 15_000; socketTimeout = 15_000 }
        }
    }

    suspend fun activateLicense(key: String, deviceId: String): Result<LicenseResponse> {
        return executeWithRetry("/license") {
            val response = client.post("$baseUrl/license") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(LicenseRequest(key, deviceId)))
            }
            if (response.status.isSuccess()) {
                Result.success(parseLicenseResponse(response.bodyAsText()))
            } else {
                val err = parseErrorResponse(response.bodyAsText())
                Result.failure(ApiException(response.status.value, err.error, err.status))
            }
        }
    }

    suspend fun createVpnKey(key: String, deviceId: String): Result<VpnResponse> {
        return executeWithRetry("/vpn/create") {
            val response = client.post("$baseUrl/vpn/create") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(LicenseRequest(key, deviceId)))
            }
            if (response.status.isSuccess()) {
                Result.success(parseVpnResponse(response.bodyAsText()))
            } else {
                val err = parseErrorResponse(response.bodyAsText())
                Result.failure(ApiException(response.status.value, err.error, err.status))
            }
        }
    }

    suspend fun getVpnKey(key: String, deviceId: String): Result<VpnResponse> {
        return executeWithRetry("/vpn/get") {
            val response = client.post("$baseUrl/vpn/get") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(LicenseRequest(key, deviceId)))
            }
            if (response.status.isSuccess()) {
                Result.success(parseVpnResponse(response.bodyAsText()))
            } else {
                val err = parseErrorResponse(response.bodyAsText())
                Result.failure(ApiException(response.status.value, err.error, err.status))
            }
        }
    }

    suspend fun checkHealth(): Result<HealthResponse> {
        return executeWithRetry("/health") {
            val response = client.get("$baseUrl/health")
            Result.success(parseHealthResponse(response.bodyAsText()))
        }
    }

    suspend fun checkVpnStatus(): Result<HealthResponse> {
        return executeWithRetry("/vpn/status") {
            val response = client.get("$baseUrl/vpn/status")
            Result.success(parseHealthResponse(response.bodyAsText()))
        }
    }

    fun close() {
        client.close()
    }

    /**
     * Retries on transient failures (IOException, 5xx) with exponential backoff.
     * Non-retryable failures (4xx ApiException) are returned immediately.
     */
    private suspend fun <T> executeWithRetry(
        endpoint: String,
        maxAttempts: Int = MAX_ATTEMPTS,
        block: suspend () -> Result<T>
    ): Result<T> {
        var lastException: Exception? = null
        for (attempt in 1..maxAttempts) {
            if (attempt > 1) delay(RETRY_DELAYS_MS[(attempt - 2).coerceAtMost(RETRY_DELAYS_MS.lastIndex)])
            try {
                val result = block()
                VizoLogger.apiCall(endpoint, result.isSuccess,
                    (result.exceptionOrNull() as? ApiException)?.httpStatus)
                // Don't retry 4xx client errors — they won't change on retry
                if (result.isFailure) {
                    val ex = result.exceptionOrNull()
                    if (ex is ApiException && ex.httpStatus in 400..499) return result
                }
                if (result.isSuccess) return result
                lastException = result.exceptionOrNull() as? Exception
            } catch (e: IOException) {
                lastException = e
                VizoLogger.apiCall(endpoint, false)
            } catch (e: Exception) {
                VizoLogger.apiCall(endpoint, false)
                return Result.failure(e)
            }
        }
        return Result.failure(lastException ?: IOException("Request failed after retries"))
    }

    companion object {
        private const val MAX_ATTEMPTS = 3  // 1 initial + 2 retries
        private val RETRY_DELAYS_MS = longArrayOf(1000L, 2000L, 4000L)

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parseLicenseResponse(body: String): LicenseResponse = json.decodeFromString(body)
        fun parseVpnResponse(body: String): VpnResponse = json.decodeFromString(body)
        fun parseHealthResponse(body: String): HealthResponse = json.decodeFromString(body)

        fun parseErrorResponse(body: String): ErrorResponse {
            return try {
                json.decodeFromString(body)
            } catch (_: Exception) {
                VizoLogger.w(Tag.API, "Non-JSON error response: ${body.take(200)}")
                ErrorResponse("Server error", "")
            }
        }
    }
}

class ApiException(val httpStatus: Int, override val message: String, val status: String) : Exception(message)
