package com.tpv.pda.ui

import com.tpv.pda.data.api.SalonTableResponse
import com.tpv.pda.data.api.TicketLineResponse
import com.tpv.pda.data.api.TicketResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TableExitPolicyTest {

    @Test
    fun buildPlan_whenTicketIsEmpty_returnsCancelIdAndTable() {
        val plan = TableExitPolicy.buildPlan(
            currentTable = table(7),
            currentTicket = ticket(id = 99L, lines = emptyList()),
            lockedTableNumber = 7
        )

        assertEquals(7, plan.tableToUnlock)
        assertEquals(99L, plan.emptyTicketToCancel)
    }

    @Test
    fun buildPlan_whenTicketHasLines_doesNotCancelTicket() {
        val plan = TableExitPolicy.buildPlan(
            currentTable = table(7),
            currentTicket = ticket(
                id = 99L,
                lines = listOf(
                    TicketLineResponse(
                        id = 1L,
                        productId = 10L,
                        productName = "Agua",
                        destination = "BAR",
                        sent = false,
                        unitPriceCents = 150,
                        qty = 1,
                        lineTotalCents = 150
                    )
                )
            ),
            lockedTableNumber = 7
        )

        assertEquals(7, plan.tableToUnlock)
        assertNull(plan.emptyTicketToCancel)
    }

    @Test
    fun buildPlan_withoutCurrentTable_fallsBackToLockedTable() {
        val plan = TableExitPolicy.buildPlan(
            currentTable = null,
            currentTicket = null,
            lockedTableNumber = 12
        )

        assertEquals(12, plan.tableToUnlock)
        assertNull(plan.emptyTicketToCancel)
    }

    private fun table(number: Int): SalonTableResponse =
        SalonTableResponse(
            tableNumber = number,
            salonName = "Salon",
            tableAlias = null,
            status = "LOCKED",
            ticketId = 99L,
            totalCents = 0,
            elapsedMinutes = 0,
            pendingLines = 0,
            lockedBy = "tester",
            lockedTerminalId = "PDA-A",
            lockExpiresAt = null
        )

    private fun ticket(id: Long, lines: List<TicketLineResponse>): TicketResponse =
        TicketResponse(
            id = id,
            tableNumber = 7,
            status = "OPEN",
            billRequested = false,
            totalBeforeDiscountCents = 0,
            discountCents = 0,
            totalCents = 0,
            lines = lines
        )
}

