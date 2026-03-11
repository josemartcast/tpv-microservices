package com.tpv.pda.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tpv.pda.data.ApiClientFactory
import com.tpv.pda.data.SessionData
import com.tpv.pda.data.SessionStore
import com.tpv.pda.data.api.LoginRequest
import com.tpv.pda.data.api.SalonTableResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class MainUiState(
    val loading: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val baseUrl: String = "http://localhost:8080",
    val username: String = "",
    val password: String = "",
    val terminalId: String = "PDA-1",
    val token: String = "",
    val loggedIn: Boolean = false,
    val tables: List<SalonTableResponse> = emptyList()
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val sessionStore = SessionStore(app.applicationContext)
    private val apiFactory = ApiClientFactory()

    private val _ui = MutableStateFlow(MainUiState())
    val ui: StateFlow<MainUiState> = _ui.asStateFlow()

    init {
        val session = sessionStore.load()
        _ui.update {
            it.copy(
                baseUrl = session.baseUrl,
                username = session.username,
                terminalId = session.terminalId,
                token = session.token,
                loggedIn = session.token.isNotBlank()
            )
        }
        if (session.token.isNotBlank()) {
            refreshTables()
        }
    }

    fun onBaseUrlChange(value: String) = _ui.update { it.copy(baseUrl = value) }
    fun onUsernameChange(value: String) = _ui.update { it.copy(username = value) }
    fun onPasswordChange(value: String) = _ui.update { it.copy(password = value) }
    fun onTerminalChange(value: String) = _ui.update { it.copy(terminalId = value) }
    fun clearMessage() = _ui.update { it.copy(message = null, error = null) }

    fun login() {
        val state = _ui.value
        val username = state.username.trim()
        val password = state.password
        val terminalId = state.terminalId.trim()
        val baseUrl = apiFactory.normalizeBaseUrl(state.baseUrl)

        if (username.isBlank() || password.isBlank() || terminalId.isBlank() || baseUrl.isBlank()) {
            _ui.update { it.copy(error = "Completa servidor, usuario, password y terminal.") }
            return
        }

        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null, message = null) }
            try {
                val auth = apiFactory.authApi(baseUrl)
                val response = auth.login(LoginRequest(username = username, password = password))
                val token = response.accessToken
                if (token.isBlank()) {
                    throw IllegalStateException("Login sin accessToken")
                }

                val session = SessionData(
                    baseUrl = baseUrl.trimEnd('/'),
                    username = username,
                    terminalId = terminalId,
                    token = token
                )
                sessionStore.save(session)

                _ui.update {
                    it.copy(
                        loading = false,
                        baseUrl = session.baseUrl,
                        username = username,
                        password = "",
                        terminalId = terminalId,
                        token = token,
                        loggedIn = true,
                        message = "Login OK"
                    )
                }
                refreshTables()
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        loading = false,
                        error = errorText("No se pudo iniciar sesión", e)
                    )
                }
            }
        }
    }

    fun logout() {
        sessionStore.clearToken()
        _ui.update {
            it.copy(
                token = "",
                loggedIn = false,
                tables = emptyList(),
                password = "",
                message = "Sesión cerrada"
            )
        }
    }

    fun refreshTables() {
        val state = _ui.value
        if (state.token.isBlank() || state.terminalId.isBlank()) {
            _ui.update { it.copy(error = "No hay sesión activa.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null, message = null) }
            try {
                val pos = apiFactory.posApi(
                    rawBaseUrl = state.baseUrl,
                    token = state.token,
                    terminalId = state.terminalId
                )
                val tables = pos.listTables().sortedBy { it.tableNumber }
                _ui.update {
                    it.copy(
                        loading = false,
                        tables = tables,
                        message = "Mesas sincronizadas: ${tables.size}"
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = errorText("No se pudieron cargar las mesas", e)) }
            }
        }
    }

    private fun errorText(prefix: String, error: Exception): String {
        val detail = when (error) {
            is HttpException -> "HTTP ${error.code()}"
            else -> error.message ?: error.javaClass.simpleName
        }
        return "$prefix: $detail"
    }
}
