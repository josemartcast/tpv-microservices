package com.tpv.pda.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tpv.pda.data.api.SalonTableResponse

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
        if (state.loggedIn) {
            TablesScreen(
                state = state,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onRefresh = viewModel::refreshTables,
                onLogout = viewModel::logout
            )
        } else {
            LoginScreen(
                state = state,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onBaseUrlChange = viewModel::onBaseUrlChange,
                onUsernameChange = viewModel::onUsernameChange,
                onPasswordChange = viewModel::onPasswordChange,
                onTerminalChange = viewModel::onTerminalChange,
                onLogin = viewModel::login
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
            text = "Configura servidor, terminal y credenciales para conectar con el backend actual.",
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
                Text("Iniciar sesión")
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
    onLogout: () -> Unit
) {
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
                Text(
                    "${state.username} - ${state.terminalId}",
                    style = MaterialTheme.typography.bodySmall
                )
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
        HorizontalDivider()

        if (state.loading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        }

        if (state.tables.isEmpty() && !state.loading) {
            Text("Sin mesas para mostrar.")
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items = state.tables, key = { it.tableNumber }) { table ->
                    TableCard(table = table)
                }
            }
        }
    }
}

@Composable
private fun TableCard(table: SalonTableResponse) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Mesa ${table.tableNumber}${aliasSuffix(table.tableAlias)}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "${table.salonName ?: "-"} · ${statusText(table.status)} · ${elapsed(table.elapsedMinutes)}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Total: ${eur(table.totalCents)}",
                style = MaterialTheme.typography.bodyMedium
            )
            if (table.lockedTerminalId?.isNotBlank() == true) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Bloqueada por ${table.lockedTerminalId}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
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
