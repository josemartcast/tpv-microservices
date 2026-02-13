package com.tpv.desktop.api.pos;

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
