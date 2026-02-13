package com.tpv.desktop.api.pos;

import java.time.Instant;

public record CashIncidentResponse(
        long id,
        long cashSessionId,
        String direction,
        int amountCents,
        String note,
        String createdBy,
        Instant createdAt
) {
}
