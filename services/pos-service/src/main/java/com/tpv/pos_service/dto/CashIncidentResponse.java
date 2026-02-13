package com.tpv.pos_service.dto;

import com.tpv.pos_service.domain.CashIncidentDirection;
import java.time.Instant;

public record CashIncidentResponse(
        long id,
        long cashSessionId,
        CashIncidentDirection direction,
        int amountCents,
        String note,
        String createdBy,
        Instant createdAt
) {
}
