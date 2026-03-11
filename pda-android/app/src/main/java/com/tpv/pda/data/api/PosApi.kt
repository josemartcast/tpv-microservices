package com.tpv.pda.data.api

import retrofit2.http.GET

interface PosApi {
    @GET("/api/v1/pos/salon/tables")
    suspend fun listTables(): List<SalonTableResponse>
}
