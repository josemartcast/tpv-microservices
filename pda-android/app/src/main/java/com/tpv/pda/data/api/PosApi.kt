package com.tpv.pda.data.api

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface PosApi {
    @GET("/api/v1/pos/salon/tables")
    suspend fun listTables(): List<SalonTableResponse>

    @POST("/api/v1/pos/salon/tables/{tableNumber}/lock")
    suspend fun lockTable(
        @Path("tableNumber") tableNumber: Int,
        @Body request: TableLockRequest
    ): TableLockResponse

    @POST("/api/v1/pos/salon/tables/{tableNumber}/heartbeat")
    suspend fun heartbeatTable(
        @Path("tableNumber") tableNumber: Int,
        @Body request: TableLockRequest
    ): TableLockResponse

    @POST("/api/v1/pos/salon/tables/{tableNumber}/unlock")
    suspend fun unlockTable(
        @Path("tableNumber") tableNumber: Int,
        @Body request: TableLockRequest
    )

    @POST("/api/v1/pos/salon/tables/{tableNumber}/open-ticket")
    suspend fun openTicket(@Path("tableNumber") tableNumber: Int): TicketResponse

    @GET("/api/v1/pos/categories")
    suspend fun listCategories(): List<CategoryResponse>

    @GET("/api/v1/pos/products")
    suspend fun listProducts(@Query("categoryId") categoryId: Long? = null): List<ProductResponse>

    @GET("/api/v1/pos/tickets/{id}")
    suspend fun getTicket(@Path("id") id: Long): TicketResponse

    @POST("/api/v1/pos/tickets/{id}/lines")
    suspend fun addLine(@Path("id") id: Long, @Body request: AddTicketLineRequest): TicketResponse

    @PATCH("/api/v1/pos/tickets/{id}/lines/{lineId}")
    suspend fun updateLineQty(
        @Path("id") id: Long,
        @Path("lineId") lineId: Long,
        @Body request: UpdateLineQtyRequest
    ): TicketResponse

    @PATCH("/api/v1/pos/tickets/{id}/lines/{lineId}/price")
    suspend fun updateLinePrice(
        @Path("id") id: Long,
        @Path("lineId") lineId: Long,
        @Body request: UpdateLinePriceRequest
    ): TicketResponse

    @DELETE("/api/v1/pos/tickets/{id}/lines/{lineId}")
    suspend fun deleteLine(@Path("id") id: Long, @Path("lineId") lineId: Long): TicketResponse

    @POST("/api/v1/pos/tickets/{id}/cancel-empty")
    suspend fun cancelEmptyTicket(@Path("id") id: Long): TicketResponse

    @GET("/api/v1/pos/tickets/{id}/send-preview")
    suspend fun sendPreview(@Path("id") id: Long): SendPreviewResponse

    @POST("/api/v1/pos/tickets/{id}/send")
    suspend fun sendComanda(@Path("id") id: Long, @Body request: SendComandaRequest): SendComandaResponse

    @GET("/api/v1/pos/tickets/{id}/payment-summary")
    suspend fun paymentSummary(@Path("id") id: Long): PaymentSummaryResponse

    @POST("/api/v1/pos/tickets/{ticketId}/payments")
    suspend fun addPayment(@Path("ticketId") ticketId: Long, @Body request: CreatePaymentRequest): PaymentResponse

    @POST("/api/v1/pos/tickets/{id}/move-table")
    suspend fun moveTable(@Path("id") id: Long, @Body request: MoveTableRequest): TicketResponse
}
