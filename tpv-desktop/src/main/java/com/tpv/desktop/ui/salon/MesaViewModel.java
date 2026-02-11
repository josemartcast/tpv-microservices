package com.tpv.desktop.ui.salon;

import java.time.Instant;

public record MesaViewModel(
        long id,
        String name,
        MesaStatus status,
        Long ticketId,
        int elapsedMinutes,
        int totalCents,
        int pendingItems,
        String waiterName,
        String lockedBy,
        String lockedTerminalId,
        Instant lockExpiresAt
) {
    public boolean hasActiveLock() {
        return (lockedBy != null && !lockedBy.isBlank())
                || (lockedTerminalId != null && !lockedTerminalId.isBlank())
                || lockExpiresAt != null;
    }
}
