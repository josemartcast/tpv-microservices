package com.tpv.desktop.api.pos;

import java.time.Instant;
import java.util.List;

public record TicketResponse(
    long id,
    Integer tableNumber,
    String status,
    boolean billRequested,
    int totalBeforeDiscountCents,
    int discountCents,
    int totalCents,
    Instant createdAt,
    Instant updatedAt,
    List<TicketLineResponse> lines
) {}
