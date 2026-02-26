package com.tpv.desktop.api.pos;

public record OpenFiscalExerciseRequest(
        int fiscalYear,
        String note
) {
}

