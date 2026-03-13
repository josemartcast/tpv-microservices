package com.tpv.pda.ui

import com.tpv.pda.data.api.SalonTableResponse
import com.tpv.pda.data.api.TicketResponse

data class TableExitPlan(
    val tableToUnlock: Int?,
    val emptyTicketToCancel: Long?
)

object TableExitPolicy {
    fun buildPlan(
        currentTable: SalonTableResponse?,
        currentTicket: TicketResponse?,
        lockedTableNumber: Int?
    ): TableExitPlan {
        val tableToUnlock = currentTable?.tableNumber ?: lockedTableNumber
        val emptyTicketToCancel = currentTicket?.takeIf { it.lines.isEmpty() }?.id
        return TableExitPlan(
            tableToUnlock = tableToUnlock,
            emptyTicketToCancel = emptyTicketToCancel
        )
    }
}

