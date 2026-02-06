package com.tpv.desktop.api.pos;

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
