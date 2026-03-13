package com.tpv.pda.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tpv.pda.data.api.ProductResponse
import com.tpv.pda.data.api.SalonTableResponse
import com.tpv.pda.data.api.TicketLineResponse

@Composable
fun PdaApp(viewModel: MainViewModel) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.error, state.message) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbar) }
    ) { padding ->
        when (state.screen) {
            ScreenMode.LOGIN -> LoginScreen(
                state = state,
                modifier = Modifier.fillMaxSize().padding(padding),
                onBaseUrlChange = viewModel::onBaseUrlChange,
                onUsernameChange = viewModel::onUsernameChange,
                onPasswordChange = viewModel::onPasswordChange,
                onTerminalChange = viewModel::onTerminalChange,
                onLogin = viewModel::login
            )

            ScreenMode.TABLES -> TablesScreen(
                state = state,
                modifier = Modifier.fillMaxSize().padding(padding),
                onRefresh = viewModel::refreshTables,
                onOpenTable = viewModel::openTable,
                onSalonFilterChange = viewModel::onSalonFilterChange,
                onLogout = viewModel::logout
            )

            ScreenMode.ORDER -> OrderScreen(
                state = state,
                modifier = Modifier.fillMaxSize().padding(padding),
                onBack = viewModel::backToTables,
                onQtyChange = viewModel::onQtyInputChange,
                onSelectCategory = viewModel::selectCategory,
                onAddProduct = viewModel::addProduct,
                onSelectLine = viewModel::selectLine,
                onUpdateQty = viewModel::updateSelectedLineQty,
                onUpdatePrice = viewModel::updateSelectedLinePrice,
                onDeleteLine = viewModel::deleteSelectedLine,
                onSendComanda = viewModel::sendComanda,
                onPay = viewModel::payTicket,
                onMoveTable = viewModel::moveCurrentTicket
            )
        }
    }
}

@Composable
private fun LoginScreen(
    state: MainUiState,
    modifier: Modifier = Modifier,
    onBaseUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTerminalChange: (String) -> Unit,
    onLogin: () -> Unit
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "TPV PDA Android", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Servidor configurable para Tailscale, local o cloud.",
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedTextField(
            value = state.baseUrl,
            onValueChange = onBaseUrlChange,
            label = { Text("Servidor (Gateway)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.terminalId,
            onValueChange = onTerminalChange,
            label = { Text("Terminal ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.username,
            onValueChange = onUsernameChange,
            label = { Text("Usuario") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = onLogin,
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.loading) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(18.dp))
            } else {
                Text("Iniciar sesion")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TablesScreen(
    state: MainUiState,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit,
    onOpenTable: (SalonTableResponse) -> Unit,
    onSalonFilterChange: (String) -> Unit,
    onLogout: () -> Unit
) {
    val salonOptions = remember(state.tables) {
        listOf("ALL") + state.tables
            .mapNotNull { it.salonName?.trim()?.takeIf(String::isNotBlank) }
            .distinct()
            .sorted()
    }
    val filteredTables = remember(state.tables, state.salonFilter) {
        if (state.salonFilter == "ALL") state.tables
        else state.tables.filter { (it.salonName ?: "").equals(state.salonFilter, ignoreCase = true) }
    }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Mesas", style = MaterialTheme.typography.headlineSmall)
                Text("${state.username} - ${state.terminalId}", style = MaterialTheme.typography.bodySmall)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRefresh, enabled = !state.loading) { Text("Refrescar") }
                TextButton(onClick = onLogout) { Text("Salir") }
            }
        }

        Text(
            text = "Servidor: ${state.baseUrl}",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            items(salonOptions, key = { it }) { option ->
                val active = option == state.salonFilter
                TextButton(
                    onClick = { onSalonFilterChange(option) },
                    modifier = if (active) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                    else Modifier
                ) {
                    Text(if (option == "ALL") "Todos" else option)
                }
            }
        }
        HorizontalDivider()

        if (state.loading) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        }

        if (filteredTables.isEmpty() && !state.loading) {
            Text("Sin mesas para mostrar.")
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items = filteredTables, key = { it.tableNumber }) { table ->
                    TableCard(table = table, onOpen = { onOpenTable(table) })
                }
            }
        }
    }
}

@Composable
private fun TableCard(table: SalonTableResponse, onOpen: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Mesa ${table.tableNumber}${aliasSuffix(table.tableAlias)}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "${table.salonName ?: "-"} | ${statusText(table.status)} | ${elapsed(table.elapsedMinutes)}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(text = "Total: ${eur(table.totalCents)}", style = MaterialTheme.typography.bodyMedium)
            if (table.lockedTerminalId?.isNotBlank() == true) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "Bloqueada por ${table.lockedTerminalId}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrderScreen(
    state: MainUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onQtyChange: (String) -> Unit,
    onSelectCategory: (Long) -> Unit,
    onAddProduct: (Long) -> Unit,
    onSelectLine: (Long?) -> Unit,
    onUpdateQty: (Int) -> Unit,
    onUpdatePrice: (Int) -> Unit,
    onDeleteLine: () -> Unit,
    onSendComanda: (String) -> Unit,
    onPay: (String, Int?) -> Unit,
    onMoveTable: (Int) -> Unit
) {
    val ticket = state.currentTicket
    val table = state.currentTable
    val selectedLine = ticket?.lines?.firstOrNull { it.id == state.selectedLineId }
    var showQtyDialog by remember { mutableStateOf(false) }
    var showPriceDialog by remember { mutableStateOf(false) }
    var showPayDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    val topScroll = rememberScrollState()
    val config = LocalConfiguration.current
    val isCompactMobile = config.screenWidthDp < 600

    BackHandler(onBack = onBack)

    Column(
        modifier = modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp)
                .verticalScroll(topScroll),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("Volver") }
                Text(
                    text = if (table == null) "Mesa" else "Mesa ${table.tableNumber}${aliasSuffix(table.tableAlias)}",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(text = table?.salonName ?: "-", style = MaterialTheme.typography.bodySmall)
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Ticket", style = MaterialTheme.typography.titleMedium)
                    if (ticket == null || ticket.lines.isEmpty()) {
                        Text("Sin lineas")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(ticket.lines, key = { it.id }) { line ->
                                TicketLineRow(
                                    line = line,
                                    selected = line.id == state.selectedLineId,
                                    onClick = { onSelectLine(line.id) }
                                )
                            }
                        }
                    }
                    Text("Total: ${eur(ticket?.totalCents ?: 0)}")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { showQtyDialog = true },
                            enabled = selectedLine != null,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) { Text("Editar qty") }
                        Button(
                            onClick = { showPriceDialog = true },
                            enabled = selectedLine != null,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) { Text("Editar precio") }
                        Button(
                            onClick = onDeleteLine,
                            enabled = selectedLine != null,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) { Text("Borrar") }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Cantidad", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = state.qtyInput,
                        onValueChange = onQtyChange,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("1", "2", "3", "5", "10").forEach { qty ->
                            TextButton(onClick = { onQtyChange(qty) }) { Text(qty) }
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Operaciones", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Pendiente enviar: ${state.pendingSendLines} lineas | Pendiente cobro: ${eur(state.pendingPaymentCents)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { onSendComanda("ALL") },
                            enabled = state.pendingSendLines > 0 && !state.loading,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) { Text("Enviar") }
                        Button(
                            onClick = { onSendComanda("BAR") },
                            enabled = state.pendingSendLines > 0 && !state.loading,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) { Text("BAR") }
                        Button(
                            onClick = { onSendComanda("COCINA") },
                            enabled = state.pendingSendLines > 0 && !state.loading,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) { Text("COCINA") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { showPayDialog = true },
                            enabled = state.pendingPaymentCents > 0 && !state.loading,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) { Text("Cobrar") }
                        Button(
                            onClick = { showMoveDialog = true },
                            enabled = !state.loading,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) { Text("Mover mesa") }
                    }
                }
            }

            if (state.categories.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    items(state.categories, key = { it.id }) { category ->
                        val active = category.id == state.activeCategoryId
                        TextButton(
                            onClick = { onSelectCategory(category.id) },
                            modifier = if (active) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                            else Modifier
                        ) {
                            Text(category.name)
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (state.products.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Center) {
                    Text("No hay productos en la categoria.")
                }
            } else {
                LazyVerticalGrid(
                    columns = if (isCompactMobile) GridCells.Fixed(2) else GridCells.Adaptive(150.dp),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.products, key = { it.id }) { product ->
                        ProductCard(product = product, onAdd = { onAddProduct(product.id) })
                    }
                }
            }
        }
    }

    if (showQtyDialog && selectedLine != null) {
        NumberDialog(
            title = "Nueva cantidad",
            initial = selectedLine.qty.toString(),
            onDismiss = { showQtyDialog = false },
            onConfirm = { value ->
                val qty = value.toIntOrNull()
                if (qty != null && qty > 0) {
                    onUpdateQty(qty)
                }
                showQtyDialog = false
            }
        )
    }

    if (showPriceDialog && selectedLine != null) {
        NumberDialog(
            title = "Nuevo precio (EUR)",
            initial = String.format("%.2f", selectedLine.unitPriceCents / 100.0),
            decimal = true,
            onDismiss = { showPriceDialog = false },
            onConfirm = { value ->
                val normalized = value.replace(',', '.')
                val priceEur = normalized.toDoubleOrNull()
                if (priceEur != null && priceEur >= 0.0) {
                    onUpdatePrice((priceEur * 100.0).toInt())
                }
                showPriceDialog = false
            }
        )
    }

    if (showPayDialog && ticket != null) {
        PayDialog(
            pendingCents = state.pendingPaymentCents,
            onDismiss = { showPayDialog = false },
            onPay = { method, amountCents ->
                onPay(method, amountCents)
                showPayDialog = false
            }
        )
    }

    if (showMoveDialog && table != null) {
        MoveTableDialog(
            currentTableNumber = table.tableNumber,
            candidates = state.tables
                .filter { it.tableNumber != table.tableNumber && it.ticketId == null && it.lockedTerminalId.isNullOrBlank() }
                .sortedBy { it.tableNumber },
            onDismiss = { showMoveDialog = false },
            onMove = { target ->
                onMoveTable(target)
                showMoveDialog = false
            }
        )
    }
}

@Composable
private fun ProductCard(product: ProductResponse, onAdd: () -> Unit) {
    Button(
        onClick = onAdd,
        modifier = Modifier.fillMaxWidth().heightIn(min = 92.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF8AB4FF)),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF1F4E79),
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = product.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = eur(product.priceCents),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun TicketLineRow(line: TicketLineResponse, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent
    Row(
        modifier = Modifier.fillMaxWidth().background(bg).clickable(onClick = onClick).padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${line.qty}x ${line.productName}", maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sentText = if (line.sent) "enviado" else "pendiente"
            Text("${line.destination ?: "-"} | $sentText", style = MaterialTheme.typography.bodySmall)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(eur(line.lineTotalCents))
            Text("u: ${eur(line.unitPriceCents)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NumberDialog(
    title: String,
    initial: String,
    decimal: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(TextFieldValue(initial)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(value.text.trim()) }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    val clean = if (decimal) {
                        it.text.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }
                    } else {
                        it.text.filter { ch -> ch.isDigit() }
                    }
                    value = it.copy(text = clean)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PayDialog(
    pendingCents: Int,
    onDismiss: () -> Unit,
    onPay: (String, Int?) -> Unit
) {
    var partialText by remember { mutableStateOf(TextFieldValue("")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        title = { Text("Cobro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pendiente: ${eur(pendingCents)}")
                OutlinedTextField(
                    value = partialText,
                    onValueChange = {
                        val clean = it.text.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }
                        partialText = it.copy(text = clean)
                    },
                    label = { Text("Parcial EUR (opcional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                val parsedPartial = partialText.text.replace(',', '.').toDoubleOrNull()?.let { (it * 100.0).toInt() }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onPay("CASH", null) }) { Text("Total CASH") }
                    Button(onClick = { onPay("CARD", null) }) { Text("Total CARD") }
                    Button(onClick = { onPay("BIZUM", null) }) { Text("Total BIZUM") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onPay("CASH", parsedPartial) },
                        enabled = parsedPartial != null && parsedPartial > 0
                    ) { Text("Parcial CASH") }
                    Button(
                        onClick = { onPay("CARD", parsedPartial) },
                        enabled = parsedPartial != null && parsedPartial > 0
                    ) { Text("Parcial CARD") }
                    Button(
                        onClick = { onPay("BIZUM", parsedPartial) },
                        enabled = parsedPartial != null && parsedPartial > 0
                    ) { Text("Parcial BIZUM") }
                }
            }
        }
    )
}

@Composable
private fun MoveTableDialog(
    currentTableNumber: Int,
    candidates: List<SalonTableResponse>,
    onDismiss: () -> Unit,
    onMove: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        title = { Text("Mover mesa") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Mesa origen: $currentTableNumber")
                if (candidates.isEmpty()) {
                    Text("No hay mesas libres disponibles.")
                } else {
                    LazyColumn(modifier = Modifier.height(240.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(candidates, key = { it.tableNumber }) { table ->
                            Card(modifier = Modifier.fillMaxWidth().clickable { onMove(table.tableNumber) }) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Mesa ${table.tableNumber}${aliasSuffix(table.tableAlias)}")
                                    Text("${table.salonName ?: "-"}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

private fun statusText(raw: String?): String = when (raw?.uppercase()) {
    "FREE" -> "Libre"
    "OCCUPIED" -> "Ocupada"
    "PENDING_SEND" -> "Pendiente enviar"
    "BILL_REQUESTED" -> "Cuenta pedida"
    else -> raw ?: "-"
}

private fun elapsed(minutes: Int): String = if (minutes <= 0) "-" else "$minutes min"

private fun aliasSuffix(alias: String?): String {
    val value = alias?.trim().orEmpty()
    return if (value.isBlank()) "" else " - $value"
}

private fun eur(cents: Int): String = String.format("%.2f EUR", cents / 100.0)
