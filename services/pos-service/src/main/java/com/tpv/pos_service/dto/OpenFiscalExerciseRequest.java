package com.tpv.pos_service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OpenFiscalExerciseRequest(
        @NotNull @Min(2000) @Max(2100) Integer fiscalYear,
        String note
) {
}

