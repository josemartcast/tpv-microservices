package com.tpv.pda.data.api

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val accessToken: String,
    val expiresInSeconds: Long,
    val roles: List<String>
)

data class SalonTableResponse(
    val tableNumber: Int,
    val salonName: String?,
    val tableAlias: String?,
    val status: String?,
    val ticketId: Long?,
    val totalCents: Int,
    val elapsedMinutes: Int,
    val pendingLines: Int,
    val lockedBy: String?,
    val lockedTerminalId: String?,
    val lockExpiresAt: String?
)
