package com.tpv.pos_service.dto;

import java.time.Instant;

public record CashSessionOpenTicketResponse(
        long ticketId,
        Integer tableNumber,
        int totalCents,
        Instant createdAt
) {
}
