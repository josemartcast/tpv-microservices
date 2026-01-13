
package com.tpv.pos_service.dto;

import com.tpv.pos_service.domain.TicketStatus;
import java.time.Instant;
import java.util.List;

public record TicketResponse(
    long id,
    TicketStatus status,
    int totalCents,
    Instant createdAt,
    Instant updatedAt,
    List<TicketLineResponse> lines
) {}
