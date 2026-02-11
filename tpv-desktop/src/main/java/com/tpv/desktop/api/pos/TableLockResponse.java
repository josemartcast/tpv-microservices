package com.tpv.desktop.api.pos;

import java.time.Instant;

public record TableLockResponse(
        int tableNumber,
        String terminalId,
        String lockedBy,
        Instant expiresAt
) {
}
