
package com.tpv.pos_service.dto;

import java.time.Instant;
import java.util.List;

public record TicketResponse(
    long id,
    String status,
    int totalCents,
    Instant createdAt,
    Instant updatedAt,
    List<TicketLineResponse> lines
) {}
