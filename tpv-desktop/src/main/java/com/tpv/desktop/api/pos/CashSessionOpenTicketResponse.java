package com.tpv.desktop.api.pos;

import java.time.Instant;

public record CashSessionOpenTicketResponse(
        long ticketId,
        Integer tableNumber,
        int totalCents,
        Instant createdAt
) {
}
