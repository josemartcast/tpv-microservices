package com.tpv.pda.data

import com.tpv.pda.data.api.AuthApi
import com.tpv.pda.data.api.PosApi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ApiClientFactory {

    fun authApi(rawBaseUrl: String): AuthApi {
        return retrofit(rawBaseUrl, includeAuthHeaders = false).create(AuthApi::class.java)
    }

    fun posApi(rawBaseUrl: String, token: String, terminalId: String): PosApi {
        return retrofit(rawBaseUrl, includeAuthHeaders = true, token = token, terminalId = terminalId)
            .create(PosApi::class.java)
    }

    fun normalizeBaseUrl(raw: String): String {
        var value = raw.trim()
        if (value.isBlank()) {
            value = "http://localhost:8080"
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "http://$value"
        }
        if (!value.endsWith("/")) {
            value += "/"
        }
        return value
    }

    private fun retrofit(
        rawBaseUrl: String,
        includeAuthHeaders: Boolean,
        token: String = "",
        terminalId: String = ""
    ): Retrofit {
        val baseUrl = normalizeBaseUrl(rawBaseUrl)
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }

        val clientBuilder = OkHttpClient.Builder()
            .addInterceptor(logging)

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
}
