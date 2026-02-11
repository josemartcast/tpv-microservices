package com.tpv.desktop.tpv.domain.model;

import java.time.Instant;

public record TableLock(int tableId, String terminalId, String owner, Instant expiresAt) {
    public boolean isActive(Instant now) {
        return expiresAt != null && expiresAt.isAfter(now);
    }
}

