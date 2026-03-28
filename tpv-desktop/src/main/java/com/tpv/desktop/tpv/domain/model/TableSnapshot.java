package com.tpv.desktop.tpv.domain.model;

public record TableSnapshot(
        int tableId,
        String salonName,
        String label,
        TableStatus status,
        int totalCents,
        long elapsedMinutes,
        int pendingCount,
        boolean billRequested,
        String prebillRequestedBy,
        String prebillRequestedTerminalId,
        String lockOwner,
        String lockTerminalId,
        long orderId
) {
}

