package com.tpv.desktop.api.pos;

import java.time.Instant;

public record FiscalExerciseResponse(
        long id,
        int fiscalYear,
        String status,
        Instant openedAt,
        Instant closedAt,
        String openedBy,
        String closedBy,
        String note
) {
}

