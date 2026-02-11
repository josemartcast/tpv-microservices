package com.tpv.pos_service.dto;

import java.time.Instant;

public record SalonTableResponse(
        int tableNumber,
        String status,
        Long ticketId,
        int totalCents,
        int elapsedMinutes,
        int pendingLines,
        String lockedBy,
        String lockedTerminalId,
        Instant lockExpiresAt
) {
}
