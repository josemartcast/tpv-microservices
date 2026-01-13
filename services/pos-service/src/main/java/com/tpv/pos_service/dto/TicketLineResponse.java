package com.tpv.pos_service.dto;

import java.time.Instant;

public record TicketLineResponse(
    long id,
    long productId,
    String productName,
    int unitPriceCents,
    int qty,
    int lineTotalCents,
    Instant createdAt,
    Instant updatedAt
) {}
