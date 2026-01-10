package com.tpv.pos_service.dto;

import java.time.Instant;

public record CreateTicketResponse(
    long id,
    String status,
    int totalCents,
    Instant createdAt,
    Instant updatedAt
) {}
