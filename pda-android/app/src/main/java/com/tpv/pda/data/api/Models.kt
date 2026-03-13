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

data class TableLockRequest(
    val terminalId: String
)

data class TableLockResponse(
    val tableNumber: Int,
    val terminalId: String,
    val lockedBy: String?,
    val expiresAt: String?
)

data class CategoryResponse(
    val id: Long,
    val name: String,
    val printDestination: String?,
    val active: Boolean
)

data class ProductResponse(
    val id: Long,
    val name: String,
    val priceCents: Int,
    val active: Boolean,
    val categoryId: Long,
    val categoryName: String?,
    val categoryPrintDestination: String?,
    val vatRateBps: Int
)

data class TicketLineResponse(
    val id: Long,
    val productId: Long,
    val productName: String,
    val destination: String?,
    val sent: Boolean,
    val unitPriceCents: Int,
    val qty: Int,
    val lineTotalCents: Int
)

data class TicketResponse(
    val id: Long,
    val tableNumber: Int?,
    val status: String,
    val billRequested: Boolean,
    val totalBeforeDiscountCents: Int,
    val discountCents: Int,
    val totalCents: Int,
    val lines: List<TicketLineResponse>
)

data class AddTicketLineRequest(
    val productId: Long,
    val qty: Int
)

data class UpdateLineQtyRequest(
    val qty: Int
)

data class UpdateLinePriceRequest(
    val priceCents: Int
)

data class SendPreviewResponse(
    val ticketId: Long,
    val pendingLines: List<TicketLineResponse>
)

data class SendComandaRequest(
    val destination: String
)

data class SendComandaResponse(
    val ticketId: Long,
    val destination: String,
    val sentCount: Int,
    val sentLineIds: List<Long>
)

data class PaymentSummaryResponse(
    val ticketId: Long,
    val ticketTotalCents: Int,
    val paidCents: Int,
    val pendingCents: Int
)

data class CreatePaymentRequest(
    val method: String,
    val amountCents: Int
)

data class PaymentResponse(
    val id: Long?,
    val method: String,
    val amountCents: Int
)

data class MoveTableRequest(
    val tableNumber: Int
)
