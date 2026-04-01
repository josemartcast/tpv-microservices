package com.tpv.pda.data

import com.tpv.pda.data.api.AuthApi
import com.tpv.pda.data.api.PosApi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class ApiClientFactory {
    private val authApiCache = ConcurrentHashMap<String, AuthApi>()
    private val posApiCache = ConcurrentHashMap<String, PosApi>()

    fun authApi(rawBaseUrl: String): AuthApi {
        val baseUrl = normalizeBaseUrl(rawBaseUrl)
        return authApiCache.getOrPut(baseUrl) {
            retrofit(baseUrl, includeAuthHeaders = false).create(AuthApi::class.java)
        }
    }

    fun posApi(rawBaseUrl: String, token: String, terminalId: String): PosApi {
        val baseUrl = normalizeBaseUrl(rawBaseUrl)
        val key = "$baseUrl|$token|$terminalId"
        return posApiCache.getOrPut(key) {
            retrofit(baseUrl, includeAuthHeaders = true, token = token, terminalId = terminalId)
                .create(PosApi::class.java)
        }
    }

    fun clearPosCache() {
        posApiCache.clear()
    }

    fun clearAllCaches() {
        authApiCache.clear()
        posApiCache.clear()
    }

    fun normalizeBaseUrl(raw: String): String {
        var value = raw.trim()
        if (value.isBlank()) {
            value = "http://localhost:8080"
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "http://$value"
        }
        return try {
            val uri = URI(value)
            val scheme = uri.scheme ?: "http"
            val host = uri.host
            val port = uri.port
            if (host.isNullOrBlank()) {
                ensureTrailingSlash(value)
            } else {
                val effectivePort = if (port > 0) ":$port" else ""
                "$scheme://$host$effectivePort/"
            }
        } catch (_: Exception) {
            ensureTrailingSlash(value)
        }
    }

    private fun retrofit(
        baseUrl: String,
        includeAuthHeaders: Boolean,
        token: String = "",
        terminalId: String = ""
    ): Retrofit {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.NONE
        }

        val clientBuilder = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (includeAuthHeaders) {
            clientBuilder.addInterceptor(Interceptor { chain ->
                val reqBuilder = chain.request().newBuilder()
                if (token.isNotBlank()) {
                    reqBuilder.header("Authorization", "Bearer $token")
                }
                if (terminalId.isNotBlank()) {
                    reqBuilder.header("X-Terminal-Id", terminalId)
                }
                reqBuilder.header("X-Client-App", "PDA")
                chain.proceed(reqBuilder.build())
            })
        }

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(clientBuilder.build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun ensureTrailingSlash(value: String): String {
        return if (value.endsWith("/")) value else "$value/"
    }
}
