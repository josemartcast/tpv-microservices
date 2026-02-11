package com.tpv.pos_service.dto;

import java.time.Instant;

public record TableLockResponse(
        int tableNumber,
        String terminalId,
        String lockedBy,
        Instant expiresAt
) {
}
