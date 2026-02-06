package com.tpv.desktop.api.pos;

import java.time.Instant;
import java.util.List;

public record TicketResponse(
    long id,
    String status,     // en backend es enum, pero aquí lo tratamos como String ("OPEN","PAID"...)
    int totalCents,
    Instant createdAt,
    Instant updatedAt,
    List<TicketLineResponse> lines
) {}

