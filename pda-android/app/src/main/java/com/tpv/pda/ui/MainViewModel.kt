package com.tpv.pda.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tpv.pda.data.ApiClientFactory
import com.tpv.pda.data.SessionData
import com.tpv.pda.data.SessionStore
import com.tpv.pda.data.api.AddTicketLineRequest
import com.tpv.pda.data.api.CategoryResponse
import com.tpv.pda.data.api.LoginRequest
import com.tpv.pda.data.api.ProductResponse
import com.tpv.pda.data.api.SalonTableResponse
import com.tpv.pda.data.api.TableLockRequest
import com.tpv.pda.data.api.TicketResponse
import com.tpv.pda.data.api.UpdateLinePriceRequest
import com.tpv.pda.data.api.UpdateLineQtyRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.HttpException

enum class ScreenMode {
    LOGIN, TABLES, ORDER
}

data class MainUiState(
    val loading: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val screen: ScreenMode = ScreenMode.LOGIN,
    val baseUrl: String = "http://localhost:8080",
    val username: String = "",
    val password: String = "",
    val terminalId: String = "PDA-1",
    val token: String = "",
    val loggedIn: Boolean = false,
    val tables: List<SalonTableResponse> = emptyList(),
    val salonFilter: String = "ALL",
    val currentTable: SalonTableResponse? = null,
    val currentTicket: TicketResponse? = null,
    val categories: List<CategoryResponse> = emptyList(),
    val products: List<ProductResponse> = emptyList(),
    val activeCategoryId: Long? = null,
    val selectedLineId: Long? = null,
    val qtyInput: String = "1",
    val pendingSendLines: Int = 0,
    val pendingPaymentCents: Int = 0
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        private const val HEARTBEAT_MS = 10_000L
    }

    private val sessionStore = SessionStore(app.applicationContext)
    private val apiFactory = ApiClientFactory()
    private val productsCache = linkedMapOf<Long, List<ProductResponse>>()
    private var lockHeartbeatJob: Job? = null
    private var lockedTableNumber: Int? = null

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
                loggedIn = session.token.isNotBlank(),
                screen = if (session.token.isNotBlank()) ScreenMode.TABLES else ScreenMode.LOGIN
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
    fun onQtyInputChange(value: String) = _ui.update { it.copy(qtyInput = value.filter { ch -> ch.isDigit() }.take(3)) }
    fun onSalonFilterChange(value: String) = _ui.update { it.copy(salonFilter = value) }
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
                        screen = ScreenMode.TABLES,
                        message = "Login OK"
                    )
                }
                refreshTables()
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = errorText("No se pudo iniciar sesion", e)) }
            }
        }
    }

    fun logout() {
        val tableToUnlock = _ui.value.currentTable?.tableNumber ?: lockedTableNumber
        val emptyTicketId = _ui.value.currentTicket?.takeIf { it.lines.isEmpty() }?.id
        viewModelScope.launch {
            cancelEmptyTicket(emptyTicketId, reportErrors = false)
            releaseLock(tableToUnlock, reportErrors = false)
            lockHeartbeatJob?.cancel()
            lockHeartbeatJob = null
            lockedTableNumber = null
            sessionStore.clearToken()
            productsCache.clear()
            _ui.update {
                it.copy(
                    token = "",
                    loggedIn = false,
                    tables = emptyList(),
                    currentTable = null,
                    currentTicket = null,
                    categories = emptyList(),
                    products = emptyList(),
                    activeCategoryId = null,
                    selectedLineId = null,
                    password = "",
                    screen = ScreenMode.LOGIN,
                    message = "Sesion cerrada"
                )
            }
        }
    }

    fun refreshTables() {
        val state = _ui.value
        if (state.token.isBlank() || state.terminalId.isBlank()) {
            _ui.update { it.copy(error = "No hay sesion activa.") }
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
                        screen = if (it.screen == ScreenMode.LOGIN) ScreenMode.TABLES else it.screen,
                        message = "Mesas sincronizadas: ${tables.size}"
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = errorText("No se pudieron cargar las mesas", e)) }
            }
        }
    }

    fun openTable(table: SalonTableResponse) {
        val state = _ui.value
        if (!state.loggedIn) {
            _ui.update { it.copy(error = "Inicia sesion para continuar.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null, message = null) }
            val tableNumber = table.tableNumber
            var locked = false
            try {
                val pos = posApi()
                pos.lockTable(tableNumber, TableLockRequest(state.terminalId))
                locked = true
                lockedTableNumber = tableNumber
                startHeartbeat(tableNumber)

                val ticket = table.ticketId?.let { pos.getTicket(it) } ?: pos.openTicket(tableNumber)

                val categories = if (state.categories.isNotEmpty()) {
                    state.categories
                } else {
                    pos.listCategories().filter { it.active }.sortedBy { it.name }
                }

                val activeCategoryId = state.activeCategoryId ?: categories.firstOrNull()?.id
                val products = if (activeCategoryId == null) {
                    emptyList()
                } else {
                    loadProductsForCategory(activeCategoryId, pos)
                }

                _ui.update {
                    it.copy(
                        loading = false,
                        screen = ScreenMode.ORDER,
                        currentTable = table.copy(ticketId = ticket.id, totalCents = ticket.totalCents),
                        currentTicket = ticket,
                        categories = categories,
                        activeCategoryId = activeCategoryId,
                        products = products,
                        selectedLineId = null,
                        qtyInput = "1",
                        message = "Mesa $tableNumber abierta"
                    )
                }
                refreshOrderSummaries(ticket.id)
                refreshTablesSilent()
            } catch (e: Exception) {
                if (locked) {
                    releaseLock(tableNumber, reportErrors = false)
                }
                lockHeartbeatJob?.cancel()
                lockHeartbeatJob = null
                lockedTableNumber = null
                _ui.update { it.copy(loading = false, error = errorText("No se pudo abrir mesa", e)) }
            }
        }
    }

    fun backToTables() {
        val tableToUnlock = _ui.value.currentTable?.tableNumber ?: lockedTableNumber
        val emptyTicketId = _ui.value.currentTicket?.takeIf { it.lines.isEmpty() }?.id
        lockHeartbeatJob?.cancel()
        lockHeartbeatJob = null
        lockedTableNumber = null
        _ui.update {
            it.copy(
                screen = ScreenMode.TABLES,
                currentTable = null,
                currentTicket = null,
                selectedLineId = null,
                qtyInput = "1"
            )
        }
        viewModelScope.launch {
            cancelEmptyTicket(emptyTicketId, reportErrors = true)
            releaseLock(tableToUnlock, reportErrors = true)
            refreshTablesSilent()
        }
    }

    fun selectCategory(categoryId: Long) {
        if (_ui.value.activeCategoryId == categoryId) return
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null, activeCategoryId = categoryId) }
            try {
                val pos = posApi()
                val products = loadProductsForCategory(categoryId, pos)
                _ui.update { it.copy(loading = false, products = products) }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = errorText("No se pudieron cargar productos", e)) }
            }
        }
    }

    fun addProduct(productId: Long) {
        val ticketId = _ui.value.currentTicket?.id ?: return
        val qty = _ui.value.qtyInput.toIntOrNull()?.coerceAtLeast(1) ?: 1
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                val updated = posApi().addLine(ticketId, AddTicketLineRequest(productId = productId, qty = qty))
                applyUpdatedTicket(updated, "Linea anadida")
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = errorText("No se pudo anadir linea", e)) }
            }
        }
    }

    fun selectLine(lineId: Long?) {
        _ui.update { it.copy(selectedLineId = lineId) }
    }

    fun updateSelectedLineQty(qty: Int) {
        val state = _ui.value
        val ticketId = state.currentTicket?.id ?: return
        val lineId = state.selectedLineId ?: return
        if (qty < 1) {
            _ui.update { it.copy(error = "La cantidad debe ser mayor o igual a 1.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                val updated = posApi().updateLineQty(ticketId, lineId, UpdateLineQtyRequest(qty))
                applyUpdatedTicket(updated, "Cantidad actualizada")
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = errorText("No se pudo actualizar cantidad", e)) }
            }
        }
    }

    fun updateSelectedLinePrice(priceCents: Int) {
        val state = _ui.value
        val ticketId = state.currentTicket?.id ?: return
        val lineId = state.selectedLineId ?: return
        if (priceCents < 0) {
            _ui.update { it.copy(error = "El precio debe ser mayor o igual a 0.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                val updated = posApi().updateLinePrice(ticketId, lineId, UpdateLinePriceRequest(priceCents))
                applyUpdatedTicket(updated, "Precio actualizado")
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = errorText("No se pudo actualizar precio", e)) }
            }
        }
    }

    fun deleteSelectedLine() {
        val state = _ui.value
        val ticketId = state.currentTicket?.id ?: return
        val lineId = state.selectedLineId ?: return
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                val updated = posApi().deleteLine(ticketId, lineId)
                applyUpdatedTicket(updated, "Linea eliminada")
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = errorText("No se pudo eliminar linea", e)) }
            }
        }
    }

    fun sendComanda(destination: String) {
        val ticketId = _ui.value.currentTicket?.id ?: return
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                val response = posApi().sendComanda(ticketId, com.tpv.pda.data.api.SendComandaRequest(destination))
                val refreshed = posApi().getTicket(ticketId)
                applyUpdatedTicket(
                    refreshed,
                    "Comanda enviada ${response.destination}: ${response.sentCount} lineas"
                )
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = errorText("No se pudo enviar comanda", e)) }
            }
        }
    }

    fun payTicket(method: String, amountCents: Int?) {
        val ticketId = _ui.value.currentTicket?.id ?: return
        val pending = _ui.value.pendingPaymentCents
        val amount = (amountCents ?: pending).coerceAtLeast(1)
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                posApi().addPayment(ticketId, com.tpv.pda.data.api.CreatePaymentRequest(method = method, amountCents = amount))
                val refreshed = posApi().getTicket(ticketId)
                applyUpdatedTicket(refreshed, "Pago registrado: $method ${toEur(amount)}")
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = errorText("No se pudo registrar pago", e)) }
            }
        }
    }

    fun moveCurrentTicket(targetTableNumber: Int) {
        val ticketId = _ui.value.currentTicket?.id ?: return
        if (targetTableNumber <= 0) {
            _ui.update { it.copy(error = "Mesa destino invalida.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                val moved = posApi().moveTable(ticketId, com.tpv.pda.data.api.MoveTableRequest(targetTableNumber))
                val tables = posApi().listTables().sortedBy { it.tableNumber }
                val targetTable = tables.firstOrNull { it.tableNumber == targetTableNumber }
                _ui.update {
                    it.copy(
                        loading = false,
                        tables = tables,
                        currentTicket = moved,
                        currentTable = targetTable ?: it.currentTable?.copy(tableNumber = targetTableNumber, ticketId = moved.id),
                        selectedLineId = null,
                        message = "Mesa movida a $targetTableNumber"
                    )
                }
                refreshOrderSummaries(moved.id)
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = errorText("No se pudo mover mesa", e)) }
            }
        }
    }

    private fun applyUpdatedTicket(ticket: TicketResponse, message: String) {
        _ui.update { current ->
            current.copy(
                loading = false,
                currentTicket = ticket,
                currentTable = current.currentTable?.copy(totalCents = ticket.totalCents, ticketId = ticket.id),
                selectedLineId = if (ticket.lines.any { it.id == current.selectedLineId }) current.selectedLineId else null,
                qtyInput = "1",
                message = message
            )
        }
        refreshOrderSummaries(ticket.id)
        refreshTablesSilent()
    }

    private fun refreshOrderSummaries(ticketId: Long) {
        viewModelScope.launch {
            try {
                val pos = posApi()
                val preview = pos.sendPreview(ticketId)
                val payment = pos.paymentSummary(ticketId)
                _ui.update {
                    it.copy(
                        pendingSendLines = preview.pendingLines.size,
                        pendingPaymentCents = payment.pendingCents
                    )
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun startHeartbeat(tableNumber: Int) {
        lockHeartbeatJob?.cancel()
        lockHeartbeatJob = viewModelScope.launch {
            while (isActive) {
                delay(HEARTBEAT_MS)
                try {
                    val terminalId = _ui.value.terminalId.trim()
                    if (terminalId.isBlank()) continue
                    posApi().heartbeatTable(tableNumber, TableLockRequest(terminalId))
                } catch (_: Exception) {
                    try {
                        val terminalId = _ui.value.terminalId.trim()
                        if (terminalId.isNotBlank()) {
                            posApi().lockTable(tableNumber, TableLockRequest(terminalId))
                            _ui.update {
                                it.copy(message = "Lock recuperado en mesa $tableNumber")
                            }
                            continue
                        }
                    } catch (_: Exception) {
                    }

                    lockedTableNumber = null
                    _ui.update {
                        it.copy(
                            screen = ScreenMode.TABLES,
                            currentTable = null,
                            currentTicket = null,
                            selectedLineId = null,
                            qtyInput = "1",
                            error = "Se perdio el lock de mesa $tableNumber. Vuelve a abrir la mesa."
                        )
                    }
                    refreshTablesSilent()
                    break
                }
            }
        }
    }

    private suspend fun releaseLock(tableNumber: Int?, reportErrors: Boolean) {
        if (tableNumber == null) return
        val terminalId = _ui.value.terminalId.trim()
        if (terminalId.isBlank()) return
        try {
            posApi().unlockTable(tableNumber, TableLockRequest(terminalId))
        } catch (e: Exception) {
            if (reportErrors) {
                _ui.update { it.copy(error = errorText("No se pudo liberar lock mesa $tableNumber", e)) }
            }
        }
    }

    private suspend fun cancelEmptyTicket(ticketId: Long?, reportErrors: Boolean) {
        if (ticketId == null) return
        try {
            posApi().cancelEmptyTicket(ticketId)
        } catch (e: Exception) {
            if (e is HttpException && (e.code() == 404 || e.code() == 409)) {
                return
            }
            if (reportErrors) {
                _ui.update { it.copy(error = errorText("No se pudo cerrar ticket vacio $ticketId", e)) }
            }
        }
    }

    private suspend fun loadProductsForCategory(categoryId: Long, pos: com.tpv.pda.data.api.PosApi): List<ProductResponse> {
        val cached = productsCache[categoryId]
        if (cached != null) return cached
        val loaded = pos.listProducts(categoryId).filter { it.active }.sortedBy { it.name }
        productsCache[categoryId] = loaded
        return loaded
    }

    private suspend fun posApi(): com.tpv.pda.data.api.PosApi {
        val state = _ui.value
        return apiFactory.posApi(
            rawBaseUrl = state.baseUrl,
            token = state.token,
            terminalId = state.terminalId
        )
    }

    private fun refreshTablesSilent() {
        val state = _ui.value
        if (state.token.isBlank()) return
        viewModelScope.launch {
            try {
                val tables = posApi().listTables().sortedBy { it.tableNumber }
                _ui.update { it.copy(tables = tables) }
            } catch (_: Exception) {
            }
        }
    }

    private fun errorText(prefix: String, error: Exception): String {
        val detail = when (error) {
            is HttpException -> {
                val body = try {
                    error.response()?.errorBody()?.string()?.take(220).orEmpty()
                } catch (_: Exception) {
                    ""
                }
                if (body.isBlank()) "HTTP ${error.code()}" else "HTTP ${error.code()} -> $body"
            }
            else -> error.message ?: error.javaClass.simpleName
        }
        return "$prefix: $detail"
    }

    private fun toEur(cents: Int): String = String.format("%.2f EUR", cents / 100.0)

    override fun onCleared() {
        lockHeartbeatJob?.cancel()
        lockHeartbeatJob = null
        super.onCleared()
    }
}
