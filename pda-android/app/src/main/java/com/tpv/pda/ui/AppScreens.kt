package com.tpv.pda.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Brush
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
import com.tpv.pda.data.api.CategoryResponse
import com.tpv.pda.data.api.ProductResponse
import com.tpv.pda.data.api.SalonTableResponse
import com.tpv.pda.data.api.TicketLineResponse
import java.text.Normalizer

private object PdaPalette {
    val pageTop = Color(0xFFEFF3FB)
    val pageBottom = Color(0xFFE2E8F4)
    val panel = Color(0xFFF7F9FF)
    val panelBorder = Color(0xFFCBD7EE)
    val ink = Color(0xFF1D2740)
    val mutedInk = Color(0xFF4D5D7D)
    val primary = Color(0xFF2E69BE)
    val primaryDark = Color(0xFF24539A)
    val success = Color(0xFF4F7E3D)
    val warning = Color(0xFFB9831D)
    val danger = Color(0xFFB23D3F)
    val darkButton = Color(0xFF303A55)
    val tableFree = Color(0xFFF2FBF4)
    val tableBusy = Color(0xFFF4F7FF)
    val tablePending = Color(0xFFFFF6E5)
    val tablePrebill = Color(0xFFE7F6F2)
    val tableLocked = Color(0xFFFFEEE8)
    val selected = Color(0xFFDCE8FF)
}

private fun Modifier.pdaBackground(): Modifier = background(
    Brush.verticalGradient(
        colors = listOf(PdaPalette.pageTop, PdaPalette.pageBottom)
    )
)

@Composable
private fun panelCardColors() = CardDefaults.cardColors(containerColor = PdaPalette.panel)

@Composable
private fun primaryButtonColors() = ButtonDefaults.buttonColors(
    containerColor = PdaPalette.primary,
    contentColor = Color.White,
    disabledContainerColor = PdaPalette.primary.copy(alpha = 0.35f),
    disabledContentColor = Color.White.copy(alpha = 0.7f)
)

@Composable
private fun darkButtonColors() = ButtonDefaults.buttonColors(
    containerColor = PdaPalette.darkButton,
    contentColor = Color.White,
    disabledContainerColor = PdaPalette.darkButton.copy(alpha = 0.35f),
    disabledContentColor = Color.White.copy(alpha = 0.7f)
)

@Composable
private fun successButtonColors() = ButtonDefaults.buttonColors(
    containerColor = PdaPalette.success,
    contentColor = Color.White,
    disabledContainerColor = PdaPalette.success.copy(alpha = 0.35f),
    disabledContentColor = Color.White.copy(alpha = 0.7f)
)

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
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(hostState = snackbar) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().pdaBackground().padding(padding)) {
            when (state.screen) {
                ScreenMode.LOGIN -> LoginScreen(
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    onBaseUrlChange = viewModel::onBaseUrlChange,
                    onUsernameChange = viewModel::onUsernameChange,
                    onPasswordChange = viewModel::onPasswordChange,
                    onTerminalChange = viewModel::onTerminalChange,
                    onLogin = viewModel::login
                )

                ScreenMode.TABLES -> TablesScreen(
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    onRefresh = viewModel::refreshTables,
                    onOpenTable = viewModel::openTable,
                    onSalonFilterChange = viewModel::onSalonFilterChange,
                    onLogout = viewModel::logout
                )

                ScreenMode.ORDER -> OrderScreen(
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    onBack = { sendPending -> viewModel.backToTables(sendPending) },
                    onQtyChange = viewModel::onQtyInputChange,
                    onSelectCategory = viewModel::selectCategory,
                    onAddProduct = viewModel::addProduct,
                    onAddCombinedProduct = viewModel::addCombinedProduct,
                    onSelectLine = viewModel::selectLine,
                    onUpdateQty = viewModel::updateSelectedLineQty,
                    onUpdatePrice = viewModel::updateSelectedLinePrice,
                    onUpdateNote = viewModel::updateSelectedLineNote,
                    onDeleteLine = viewModel::deleteSelectedLine,
                    onSendComanda = viewModel::sendComanda,
                    onRequestPrebill = viewModel::requestPrebill,
                    onPay = viewModel::payTicket,
                    onMoveTable = viewModel::moveCurrentTicket
                )
            }
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
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = panelCardColors(),
            border = BorderStroke(1.dp, PdaPalette.panelBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "TPV PDA",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = PdaPalette.ink
                )
                Text(
                    text = "Conecta tu terminal al servidor del local.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PdaPalette.mutedInk
                )
                Text(
                    text = if (state.networkAvailable) "Red del dispositivo: ONLINE" else "Red del dispositivo: OFFLINE",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (state.networkAvailable) Color(0xFF1F7A3A) else PdaPalette.danger
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
                    label = { Text("Contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = onLogin,
                    enabled = !state.loading,
                    colors = primaryButtonColors(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(18.dp), color = Color.White)
                    } else {
                        Text("Iniciar sesión")
                    }
                }
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
    val config = LocalConfiguration.current
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenPadding = if (isLandscape) 8.dp else 16.dp
    val sectionSpacing = if (isLandscape) 6.dp else 10.dp
    val headerPadding = if (isLandscape) 8.dp else 12.dp
    val actionButtonMinHeight = if (isLandscape) 38.dp else 44.dp
    val filterButtonMinHeight = if (isLandscape) 36.dp else 42.dp
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

    Column(modifier = modifier.padding(screenPadding), verticalArrangement = Arrangement.spacedBy(sectionSpacing)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = panelCardColors(),
            border = BorderStroke(1.dp, PdaPalette.panelBorder)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(headerPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        "Mapa de Mesas",
                        style = if (isLandscape)
                            MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                        else
                            MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = PdaPalette.ink
                    )
                    Text(
                        "${state.username} | ${state.terminalId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PdaPalette.mutedInk
                    )
                    Text(
                        text = buildString {
                            append(if (state.networkAvailable) "RED ONLINE" else "RED OFFLINE")
                            append(" · ")
                            append(if (state.serverReachable) "SERVIDOR OK" else "SERVIDOR NO DISPONIBLE")
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (state.networkAvailable && state.serverReachable) Color(0xFF1F7A3A) else PdaPalette.warning
                    )
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Button(
                        onClick = onRefresh,
                        enabled = !state.loading,
                        colors = darkButtonColors(),
                        modifier = Modifier.widthIn(min = 112.dp).heightIn(min = actionButtonMinHeight)
                    ) {
                        Text("Refrescar", maxLines = 1, softWrap = false)
                    }
                    Button(
                        onClick = onLogout,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PdaPalette.danger,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.widthIn(min = 112.dp).heightIn(min = actionButtonMinHeight)
                    ) { Text("Salir") }
                }
            }
        }

        Text(
            text = "Servidor: ${state.baseUrl}",
            style = MaterialTheme.typography.bodySmall,
            color = PdaPalette.mutedInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            items(salonOptions, key = { it }) { option ->
                val active = option == state.salonFilter
                Button(
                    onClick = { onSalonFilterChange(option) },
                    colors = if (active) primaryButtonColors() else ButtonDefaults.buttonColors(
                        containerColor = PdaPalette.panel,
                        contentColor = PdaPalette.ink
                    ),
                    border = BorderStroke(1.dp, if (active) PdaPalette.primaryDark else PdaPalette.panelBorder),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.heightIn(min = filterButtonMinHeight)
                ) {
                    Text(if (option == "ALL") "Todos" else option)
                }
            }
        }
        if (!isLandscape) {
            HorizontalDivider(color = PdaPalette.panelBorder)
        }

        if (state.loading) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        }

        if (filteredTables.isEmpty() && !state.loading) {
            Text("Sin mesas para mostrar.")
        } else {
            LazyVerticalGrid(
                columns = if (isLandscape) GridCells.Adaptive(minSize = 240.dp) else GridCells.Fixed(1),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items = filteredTables, key = { it.tableNumber }) { table ->
                    TableCard(table = table, compact = isLandscape, onOpen = { onOpenTable(table) })
                }
            }
        }
    }
}

@Composable
private fun TableCard(table: SalonTableResponse, compact: Boolean = false, onOpen: () -> Unit) {
    val contentPadding = if (compact) 8.dp else 12.dp
    val rowSpacing = if (compact) 2.dp else 4.dp
    val fixedCompactHeight = 98.dp
    val cardBg = when (table.status?.uppercase()) {
        "FREE" -> PdaPalette.tableFree
        "PENDING_SEND" -> PdaPalette.tablePending
        "PRECUENTA_PEDIDA" -> PdaPalette.tablePrebill
        else -> if (table.lockedTerminalId?.isNotBlank() == true) PdaPalette.tableLocked else PdaPalette.tableBusy
    }
    val borderColor = when (table.status?.uppercase()) {
        "FREE" -> Color(0xFF7AB784)
        "PENDING_SEND" -> PdaPalette.warning
        "PRECUENTA_PEDIDA" -> Color(0xFF2F8F80)
        else -> if (table.lockedTerminalId?.isNotBlank() == true) PdaPalette.danger else Color(0xFF8FA5D1)
    }
    val cardModifier = if (compact) {
        Modifier
            .fillMaxWidth()
            .height(fixedCompactHeight)
            .clickable(onClick = onOpen)
    } else {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    }
    Card(
        modifier = cardModifier,
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
            Text(
                text = "Mesa ${table.tableNumber}${aliasSuffix(table.tableAlias)}",
                style = if (compact)
                    MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                else
                    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = PdaPalette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${table.salonName ?: "-"} | ${statusText(table.status)} | ${elapsed(table.elapsedMinutes)}",
                style = MaterialTheme.typography.bodySmall,
                color = PdaPalette.mutedInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if ("PRECUENTA_PEDIDA".equals(table.status, ignoreCase = true)) {
                Text(
                    text = prebillRequesterText(table),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF1F6D62),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "Total: ${eur(table.totalCents)}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = PdaPalette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (table.lockedTerminalId?.isNotBlank() == true) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Bloqueada por ${table.lockedTerminalId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = PdaPalette.danger,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrderScreen(
    state: MainUiState,
    modifier: Modifier = Modifier,
    onBack: (Boolean) -> Unit,
    onQtyChange: (String) -> Unit,
    onSelectCategory: (Long) -> Unit,
    onAddProduct: (Long) -> Unit,
    onAddCombinedProduct: (Long, Long, Int) -> Unit,
    onSelectLine: (Long?) -> Unit,
    onUpdateQty: (Int) -> Unit,
    onUpdatePrice: (Int) -> Unit,
    onUpdateNote: (String) -> Unit,
    onDeleteLine: () -> Unit,
    onSendComanda: (String) -> Unit,
    onRequestPrebill: () -> Unit,
    onPay: (String, Int?) -> Unit,
    onMoveTable: (Int) -> Unit
) {
    val ticket = state.currentTicket
    val table = state.currentTable
    val selectedLine = ticket?.lines?.firstOrNull { it.id == state.selectedLineId }
    var showQtyDialog by remember { mutableStateOf(false) }
    var showPriceDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var showPayDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var pendingCopaProduct by remember { mutableStateOf<ProductResponse?>(null) }
    var pendingComboQty by remember { mutableStateOf(1) }
    val controlsScroll = rememberScrollState()
    val ticketListState = rememberLazyListState()
    val config = LocalConfiguration.current
    val isPortrait = config.orientation == Configuration.ORIENTATION_PORTRAIT
    val isCompactMobile = config.screenWidthDp < 600
    val isCompactPortrait = isCompactMobile && isPortrait
    val isShortHeight = config.screenHeightDp <= 560
    val useWeightedSplit = isCompactPortrait || isShortHeight
    val controlsWeight = if (isShortHeight) 0.30f else 0.38f
    val productsWeight = 1f - controlsWeight
    val controlsMaxHeight = when {
        isCompactPortrait && config.screenHeightDp <= 700 -> 180.dp
        isCompactPortrait && config.screenHeightDp <= 820 -> 210.dp
        isCompactPortrait && config.screenHeightDp <= 950 -> 230.dp
        isCompactPortrait -> 250.dp
        isShortHeight -> 180.dp
        config.screenHeightDp <= 700 -> 220.dp
        config.screenHeightDp <= 820 -> 250.dp
        config.screenHeightDp <= 950 -> 280.dp
        else -> 340.dp
    }
    val ticketListMaxHeight = when {
        isShortHeight -> 72.dp
        isCompactPortrait -> 80.dp
        isCompactMobile -> 92.dp
        else -> 120.dp
    }
    val productsMinHeight = if (isCompactPortrait) 120.dp else 180.dp

    LaunchedEffect(ticket?.id, ticket?.lines?.size, ticket?.totalCents) {
        val lines = ticket?.lines ?: emptyList()
        if (lines.isNotEmpty()) {
            ticketListState.scrollToItem(lines.lastIndex)
        }
    }

    val requestBack: () -> Unit = {
        if (state.pendingSendLines > 0) {
            showExitConfirmDialog = true
        } else {
            onBack(false)
        }
    }

    BackHandler(onBack = requestBack)

    Column(
        modifier = modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = panelCardColors(),
            border = BorderStroke(1.dp, PdaPalette.panelBorder)
        ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = requestBack,
                        colors = darkButtonColors(),
                        modifier = Modifier.heightIn(min = 42.dp)
                    ) { Text("Volver") }
                    Text(
                        text = if (table == null) "Mesa" else "Mesa ${table.tableNumber}${aliasSuffix(table.tableAlias)}",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = PdaPalette.ink
                    )
                    Text(text = table?.salonName ?: "-", style = MaterialTheme.typography.bodySmall, color = PdaPalette.mutedInk)
                }

                if (state.categories.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        items(state.categories, key = { it.id }) { category ->
                            val active = category.id == state.activeCategoryId
                            Button(
                                onClick = { onSelectCategory(category.id) },
                                colors = if (active) primaryButtonColors() else ButtonDefaults.buttonColors(
                                    containerColor = PdaPalette.panel,
                                    contentColor = PdaPalette.ink
                                ),
                                border = BorderStroke(1.dp, if (active) PdaPalette.primaryDark else PdaPalette.panelBorder),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.heightIn(min = 40.dp)
                            ) {
                                Text(category.name)
                            }
                        }
                    }
                }
            }
        }

        @Composable
        fun ControlsPanel(panelModifier: Modifier) {
            Column(
                modifier = panelModifier.verticalScroll(controlsScroll),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = panelCardColors(),
                    border = BorderStroke(1.dp, PdaPalette.panelBorder)
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Ticket", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PdaPalette.ink)
                        if (ticket == null || ticket.lines.isEmpty()) {
                            Text("Sin líneas", color = PdaPalette.mutedInk)
                        } else {
                            LazyColumn(
                                state = ticketListState,
                                modifier = Modifier.fillMaxWidth().heightIn(max = ticketListMaxHeight),
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
                        Text("Total: ${eur(ticket?.totalCents ?: 0)}", color = PdaPalette.ink, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { showQtyDialog = true },
                                enabled = selectedLine != null,
                                colors = darkButtonColors(),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) { Text("Editar qty") }
                            Button(
                                onClick = { showPriceDialog = true },
                                enabled = selectedLine != null,
                                colors = darkButtonColors(),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) { Text("Editar precio") }
                            Button(
                                onClick = { showNoteDialog = true },
                                enabled = selectedLine != null,
                                colors = darkButtonColors(),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) { Text("Nota") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = onDeleteLine,
                                enabled = selectedLine != null,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PdaPalette.danger,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            ) { Text("Borrar") }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = panelCardColors(),
                    border = BorderStroke(1.dp, PdaPalette.panelBorder)
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Cantidad", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PdaPalette.ink)
                        OutlinedTextField(
                            value = state.qtyInput,
                            onValueChange = onQtyChange,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("1", "2", "3", "5", "10").forEach { qty ->
                                Button(
                                    onClick = { onQtyChange(qty) },
                                    colors = darkButtonColors(),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.heightIn(min = 40.dp)
                                ) { Text(qty) }
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = panelCardColors(),
                    border = BorderStroke(1.dp, PdaPalette.panelBorder)
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Operaciones", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PdaPalette.ink)
                        Text(
                            "Pendiente enviar: ${state.pendingSendLines} líneas | Pendiente cobro: ${eur(state.pendingPaymentCents)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { onSendComanda("ALL") },
                                enabled = state.pendingSendLines > 0 && !state.loading,
                                colors = primaryButtonColors(),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) { Text("Enviar") }
                            Button(
                                onClick = { onSendComanda("BAR") },
                                enabled = state.pendingSendLines > 0 && !state.loading,
                                colors = darkButtonColors(),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) { Text("BAR") }
                            Button(
                                onClick = { onSendComanda("COCINA") },
                                enabled = state.pendingSendLines > 0 && !state.loading,
                                colors = darkButtonColors(),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) { Text("COCINA") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = onRequestPrebill,
                                enabled = ticket != null && !state.loading,
                                colors = darkButtonColors(),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) { Text("Precuenta") }
                            Button(
                                onClick = { showPayDialog = true },
                                enabled = state.pendingPaymentCents > 0 && !state.loading,
                                colors = successButtonColors(),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) { Text("Cobrar") }
                            Button(
                                onClick = { showMoveDialog = true },
                                enabled = !state.loading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PdaPalette.warning,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) { Text("Mover mesa") }
                        }
                    }
                }
            }
        }

        @Composable
        fun ProductsPanel(panelModifier: Modifier) {
            Card(
                modifier = panelModifier,
                colors = panelCardColors(),
                border = BorderStroke(1.dp, PdaPalette.panelBorder)
            ) {
                if (state.products.isEmpty()) {
                    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Center) {
                        Text("No hay productos en la categoría.", color = PdaPalette.mutedInk)
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
                            ProductCard(
                                product = product,
                                onAdd = {
                                    if (pendingCopaProduct != null) {
                                        if (isRefrescosProduct(product, state.categories, state.activeCategoryId)) {
                                            onAddCombinedProduct(
                                                pendingCopaProduct!!.id,
                                                product.id,
                                                pendingComboQty
                                            )
                                            pendingCopaProduct = null
                                            pendingComboQty = 1
                                        }
                                    } else if (isCopasProduct(product, state.categories, state.activeCategoryId)) {
                                        pendingCopaProduct = product
                                    } else {
                                        onAddProduct(product.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (!isPortrait) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ControlsPanel(
                    panelModifier = Modifier
                        .fillMaxHeight()
                        .weight(0.40f, fill = true)
                )
                ProductsPanel(
                    panelModifier = Modifier
                        .fillMaxHeight()
                        .weight(0.60f, fill = true)
                )
            }
        } else {
            val controlsModifier = if (useWeightedSplit) {
                Modifier
                    .fillMaxWidth()
                    .weight(controlsWeight, fill = true)
            } else {
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = controlsMaxHeight)
            }
            ControlsPanel(panelModifier = controlsModifier)

            val productsModifier = if (useWeightedSplit) {
                Modifier
                    .fillMaxWidth()
                    .weight(productsWeight, fill = true)
            } else {
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = productsMinHeight)
                    .weight(1f)
            }
            ProductsPanel(panelModifier = productsModifier)
        }
    pendingCopaProduct?.let { copaProduct ->
        AlertDialog(
            onDismissRequest = { pendingCopaProduct = null },
            title = { Text("Combinar copa") },
            text = { Text("Quieres combinar esta copa con un refresco?") },
            confirmButton = {
                TextButton(onClick = {
                    pendingComboQty = state.qtyInput.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    onQtyChange("1")
                    findCategoryByName(state.categories, "REFRESCOS")?.let { refrescos ->
                        onSelectCategory(refrescos.id)
                    }
                }) { Text("Si, combinar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    onAddProduct(copaProduct.id)
                    pendingCopaProduct = null
                    pendingComboQty = 1
                }) { Text("No combinar") }
            }
        )
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

    if (showNoteDialog && selectedLine != null) {
        TextDialog(
            title = "Nota de comanda",
            initial = selectedLine.note ?: "",
            onDismiss = { showNoteDialog = false },
            onConfirm = { value ->
                onUpdateNote(value)
                showNoteDialog = false
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

    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            title = { Text("Enviar comanda") },
            text = { Text("Hay ${state.pendingSendLines} líneas pendientes. ¿Quieres enviarlas antes de salir de la mesa?") },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirmDialog = false
                    onBack(true)
                }) { Text("Si, enviar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExitConfirmDialog = false
                    onBack(false)
                }) { Text("No enviar") }
            }
        )
    }
}

}


@Composable
private fun ProductCard(product: ProductResponse, onAdd: () -> Unit) {
    Button(
        onClick = onAdd,
        modifier = Modifier.fillMaxWidth().heightIn(min = 92.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF9FBAF0)),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF2E4A78),
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
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFDCE9FF)
                )
            }
        }
    }
}

@Composable
private fun TicketLineRow(line: TicketLineResponse, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) PdaPalette.selected else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(8.dp))
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) PdaPalette.primaryDark else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "${line.qty}x ${line.productName}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = PdaPalette.ink,
                fontWeight = FontWeight.SemiBold
            )
            val sentText = if (line.sent) "enviado" else "pendiente"
            Text(
                "${line.destination ?: "-"} | $sentText",
                style = MaterialTheme.typography.bodySmall,
                color = PdaPalette.mutedInk
            )
            val note = line.note?.trim().orEmpty()
            if (note.isNotBlank()) {
                Text(
                    "Nota: $note",
                    style = MaterialTheme.typography.bodySmall,
                    color = PdaPalette.warning
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(eur(line.lineTotalCents), color = PdaPalette.ink, fontWeight = FontWeight.Bold)
            Text("u: ${eur(line.unitPriceCents)}", style = MaterialTheme.typography.bodySmall, color = PdaPalette.mutedInk)
        }
    }
}

@Composable
private fun TextDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(TextFieldValue(initial)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(value.text) }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = false,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
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
                    Button(onClick = { onPay("CASH", null) }) { Text("Total EFECTIVO") }
                    Button(onClick = { onPay("CARD", null) }) { Text("Total TARJETA") }
                    Button(onClick = { onPay("BIZUM", null) }) { Text("Total BIZUM") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onPay("CASH", parsedPartial) },
                        enabled = parsedPartial != null && parsedPartial > 0
                    ) { Text("Parcial EFECTIVO") }
                    Button(
                        onClick = { onPay("CARD", parsedPartial) },
                        enabled = parsedPartial != null && parsedPartial > 0
                    ) { Text("Parcial TARJETA") }
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
    "PRECUENTA_PEDIDA" -> "Precuenta pedida"
    "BILL_REQUESTED" -> "Cuenta pedida"
    else -> raw ?: "-"
}

private fun prebillRequesterText(table: SalonTableResponse): String {
    val actor = table.prebillRequestedBy?.trim().orEmpty()
    val terminal = table.prebillRequestedTerminalId?.trim().orEmpty()
    return when {
        actor.isNotBlank() && terminal.isNotBlank() -> "Pedida por $actor ($terminal)"
        actor.isNotBlank() -> "Pedida por $actor"
        terminal.isNotBlank() -> "Pedida por terminal $terminal"
        else -> "Precuenta solicitada"
    }
}

private fun elapsed(minutes: Int): String = if (minutes <= 0) "-" else "$minutes min"

private fun aliasSuffix(alias: String?): String {
    val value = alias?.trim().orEmpty()
    return if (value.isBlank()) "" else " - $value"
}

private fun isCopasProduct(
    product: ProductResponse,
    categories: List<CategoryResponse>,
    activeCategoryId: Long?
): Boolean {
    val fromProduct = normalizeCategoryName(product.categoryName)
    if (fromProduct == "COPAS") {
        return true
    }
    val byId = categories.firstOrNull { it.id == product.categoryId }?.name
    if (normalizeCategoryName(byId) == "COPAS") {
        return true
    }
    val activeName = categories.firstOrNull { it.id == activeCategoryId }?.name
    return normalizeCategoryName(activeName) == "COPAS"
}

private fun isRefrescosProduct(
    product: ProductResponse,
    categories: List<CategoryResponse>,
    activeCategoryId: Long?
): Boolean {
    val fromProduct = normalizeCategoryName(product.categoryName)
    if (fromProduct == "REFRESCOS") {
        return true
    }
    val byId = categories.firstOrNull { it.id == product.categoryId }?.name
    if (normalizeCategoryName(byId) == "REFRESCOS") {
        return true
    }
    val activeName = categories.firstOrNull { it.id == activeCategoryId }?.name
    return normalizeCategoryName(activeName) == "REFRESCOS"
}

private fun findCategoryByName(
    categories: List<CategoryResponse>,
    expectedName: String
): CategoryResponse? {
    val expected = normalizeCategoryName(expectedName)
    return categories.firstOrNull { normalizeCategoryName(it.name) == expected }
}

private fun normalizeCategoryName(name: String?): String {
    val raw = name?.trim().orEmpty()
    if (raw.isEmpty()) {
        return ""
    }
    return Normalizer.normalize(raw, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .uppercase()
}

private fun eur(cents: Int): String = String.format("%.2f EUR", cents / 100.0)
