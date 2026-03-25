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
import com.tpv.pda.data.api.SendComandaRequest
import com.tpv.pda.data.api.SetBillRequestedRequest
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
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

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
            _ui.update { it.copy(error = "Completa servidor, usuario, contraseña y terminal.") }
            return
        }

        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null, message = null) }
            try {
                val auth = apiFactory.authApi(baseUrl)
                val response = auth.login(LoginRequest(username = username, password = password))
                val token = response.accessToken
                if (token.isBlank()) {
                    throw IllegalStateException("Inicio de sesión incompleto")
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
                        message = "Sesión iniciada"
                    )
                }
                refreshTables()
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = errorText("No se pudo iniciar sesión", e)) }
            }
        }
    }

    fun logout() {
        val exitPlan = TableExitPolicy.buildPlan(
            currentTable = _ui.value.currentTable,
            currentTicket = _ui.value.currentTicket,
            lockedTableNumber = lockedTableNumber
        )
        viewModelScope.launch {
            cancelEmptyTicket(exitPlan.emptyTicketToCancel, reportErrors = false)
            releaseLock(exitPlan.tableToUnlock, reportErrors = false)
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
                    message = "Sesión cerrada"
                )
            }
        }
    }

    fun refreshTables() {
        val state = _ui.value
        if (state.token.isBlank() || state.terminalId.isBlank()) {
            _ui.update { it.copy(error = "No hay una sesión activa.") }
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
            _ui.update { it.copy(error = "Inicia sesión para continuar.") }
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

    fun backToTables(sendPendingComanda: Boolean = false) {
        val exitPlan = TableExitPolicy.buildPlan(
            currentTable = _ui.value.currentTable,
            currentTicket = _ui.value.currentTicket,
            lockedTableNumber = lockedTableNumber
        )
        viewModelScope.launch {
            var exitMessage: String? = null
            var exitError: String? = null
            if (sendPendingComanda) {
                val ticketId = _ui.value.currentTicket?.id
                val pendingLines = _ui.value.pendingSendLines
                if (ticketId != null && pendingLines > 0) {
                    try {
                        val response = posApi().sendComanda(ticketId, SendComandaRequest("ALL"))
                        exitMessage = "Comanda enviada (${response.sentCount} líneas)."
                    } catch (e: Exception) {
                        exitError = errorText("No se pudo enviar comanda al salir", e)
                    }
                }
            }
            lockHeartbeatJob?.cancel()
            lockHeartbeatJob = null
            lockedTableNumber = null
            _ui.update {
                it.copy(
                    screen = ScreenMode.TABLES,
                    currentTable = null,
                    currentTicket = null,
                    selectedLineId = null,
                    qtyInput = "1",
                    pendingSendLines = 0,
                    pendingPaymentCents = 0,
                    message = exitMessage,
                    error = exitError
                )
            }
            cancelEmptyTicket(exitPlan.emptyTicketToCancel, reportErrors = true)
            releaseLock(exitPlan.tableToUnlock, reportErrors = true)
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
                applyUpdatedTicket(updated, "Línea añadida")
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = errorText("No se pudo añadir la línea", e)) }
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
                _ui.update { it.copy(loading = false, error = errorText("No se pudo actualizar la cantidad", e)) }
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
                _ui.update { it.copy(loading = false, error = errorText("No se pudo eliminar la línea", e)) }
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
                    "Comanda enviada ${response.destination}: ${response.sentCount} líneas"
                )
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = errorText("No se pudo enviar comanda", e)) }
            }
        }
    }

    fun requestPrebill() {
        val ticketId = _ui.value.currentTicket?.id ?: return
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                val updated = posApi().setBillRequested(ticketId, SetBillRequestedRequest(requested = true))
                applyUpdatedTicket(updated, "Precuenta solicitada. Se imprimirá en TPV.")
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = errorText("No se pudo solicitar precuenta", e)) }
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
                val paymentMessage = "Pago registrado: $method ${toEur(amount)}"
                if (refreshed.status.equals("PAID", ignoreCase = true)) {
                    val tableToUnlock = lockedTableNumber ?: _ui.value.currentTable?.tableNumber
                    releaseLock(tableToUnlock, reportErrors = false)
                    lockHeartbeatJob?.cancel()
                    lockHeartbeatJob = null
                    lockedTableNumber = null
                    _ui.update {
                        it.copy(
                            loading = false,
                            screen = ScreenMode.TABLES,
                            currentTable = null,
                            currentTicket = null,
                            selectedLineId = null,
                            qtyInput = "1",
                            pendingSendLines = 0,
                            pendingPaymentCents = 0,
                            message = "$paymentMessage. Cuenta cerrada y mesa liberada."
                        )
                    }
                    refreshTablesSilent()
                } else {
                    applyUpdatedTicket(refreshed, paymentMessage)
                }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = errorText("No se pudo registrar pago", e)) }
            }
        }
    }

    fun moveCurrentTicket(targetTableNumber: Int) {
        val ticketId = _ui.value.currentTicket?.id ?: return
        if (targetTableNumber <= 0) {
            _ui.update { it.copy(error = "Mesa de destino inválida.") }
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
                                it.copy(message = "Bloqueo recuperado en mesa $tableNumber")
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
                            error = "Se perdió el bloqueo de la mesa $tableNumber. Vuelve a abrirla."
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
                _ui.update { it.copy(error = errorText("No se pudo liberar el bloqueo de la mesa $tableNumber", e)) }
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
                _ui.update { it.copy(error = errorText("No se pudo cerrar el ticket vacío $ticketId", e)) }
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
        return "$prefix. ${friendlyErrorDetail(error)}"
    }

    private fun friendlyErrorDetail(error: Exception): String = when (error) {
        is HttpException -> friendlyHttpError(error)
        is UnknownHostException -> "No se encontró el servidor. Revisa la dirección."
        is ConnectException -> "No se pudo conectar con el servidor. Comprueba que el TPV esté encendido."
        is SocketTimeoutException -> "El servidor tardó demasiado en responder. Inténtalo de nuevo."
        is SSLException -> "No se pudo establecer una conexión segura con el servidor."
        is IOException -> "Problema de red. Revisa la conexión y vuelve a intentarlo."
        else -> {
            val msg = error.message?.trim().orEmpty()
            if (msg.isBlank()) "Error inesperado. Inténtalo de nuevo."
            else msg.take(180)
        }
    }

    private fun friendlyHttpError(error: HttpException): String {
        val code = error.code()
        val body = readHttpErrorBody(error).lowercase()
        return when (code) {
            400 -> "Los datos no son válidos. Revisa la información e inténtalo de nuevo."
            401 -> "Sesión caducada o credenciales incorrectas. Inicia sesión de nuevo."
            403 -> "No tienes permisos para realizar esta acción."
            404 -> "No se encontró la información solicitada. Actualiza y vuelve a intentarlo."
            409 -> when {
                body.contains("lock") || body.contains("locked") ->
                    "La mesa está siendo usada en otro terminal."
                body.contains("ticket") && body.contains("open") ->
                    "No se puede completar la operación porque el ticket sigue abierto."
                body.contains("already") && body.contains("exist") ->
                    "Ese dato ya existe."
                else ->
                    "La operación entra en conflicto con el estado actual. Refresca y vuelve a intentarlo."
            }
            422 -> "Los datos enviados no son válidos para esta operación."
            in 500..599 -> "El servidor tuvo un problema temporal. Inténtalo en unos segundos."
            else -> "No se pudo completar la operación (HTTP $code)."
        }
    }

    private fun readHttpErrorBody(error: HttpException): String {
        return try {
            error.response()?.errorBody()?.string()?.trim().orEmpty().take(300)
        } catch (_: Exception) {
            ""
        }
    }

    private fun toEur(cents: Int): String = String.format("%.2f EUR", cents / 100.0)

    override fun onCleared() {
        lockHeartbeatJob?.cancel()
        lockHeartbeatJob = null
        super.onCleared()
    }
}
