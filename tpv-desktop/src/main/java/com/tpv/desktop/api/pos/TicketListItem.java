package com.tpv.desktop.api.pos;

import java.time.Instant;

public record TicketListItem(
    long id,
    String status,
    int totalCents,
    Instant createdAt
) {}
