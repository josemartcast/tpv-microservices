package com.tpv.pos_service.dto;

import com.tpv.pos_service.domain.FiscalExerciseStatus;
import java.time.Instant;

public record FiscalExerciseResponse(
        Long id,
        int fiscalYear,
        FiscalExerciseStatus status,
        Instant openedAt,
        Instant closedAt,
        String openedBy,
        String closedBy,
        String note
) {
}

