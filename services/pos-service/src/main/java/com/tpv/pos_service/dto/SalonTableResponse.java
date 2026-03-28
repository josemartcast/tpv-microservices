package com.tpv.pos_service.dto;

import java.time.Instant;

public record SalonTableResponse(
        int tableNumber,
        String salonName,
        String tableAlias,
        String status,
        Long ticketId,
        int totalCents,
        int elapsedMinutes,
        int pendingLines,
        String prebillRequestedBy,
        String prebillRequestedTerminalId,
        String lockedBy,
        String lockedTerminalId,
        Instant lockExpiresAt
) {
}
