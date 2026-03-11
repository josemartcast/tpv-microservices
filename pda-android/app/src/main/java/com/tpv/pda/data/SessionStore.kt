package com.tpv.pda.data

import android.content.Context

data class SessionData(
    val baseUrl: String = "http://localhost:8080",
    val username: String = "",
    val terminalId: String = "PDA-1",
    val token: String = ""
)

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("tpv_pda_android", Context.MODE_PRIVATE)

    fun load(): SessionData {
        return SessionData(
            baseUrl = prefs.getString(KEY_BASE_URL, "http://localhost:8080") ?: "http://localhost:8080",
            username = prefs.getString(KEY_USERNAME, "") ?: "",
            terminalId = prefs.getString(KEY_TERMINAL, "PDA-1") ?: "PDA-1",
            token = prefs.getString(KEY_TOKEN, "") ?: ""
        )
    }

    fun save(data: SessionData) {
        prefs.edit()
            .putString(KEY_BASE_URL, data.baseUrl)
            .putString(KEY_USERNAME, data.username)
            .putString(KEY_TERMINAL, data.terminalId)
            .putString(KEY_TOKEN, data.token)
            .apply()
    }

    fun clearToken() {
        prefs.edit().putString(KEY_TOKEN, "").apply()
    }

    companion object {
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_TERMINAL = "terminal_id"
        private const val KEY_TOKEN = "token"
    }
}
