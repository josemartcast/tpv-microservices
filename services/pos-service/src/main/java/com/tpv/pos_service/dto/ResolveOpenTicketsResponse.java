package com.tpv.pos_service.dto;

import java.util.List;

public record ResolveOpenTicketsResponse(
        long cashSessionId,
        int openBefore,
        int autoCancelled,
        int openAfter,
        List<Long> autoCancelledTicketIds,
        List<CashSessionOpenTicketResponse> remainingOpenTickets
) {
}
