package com.tpv.desktop.api.pos;

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
