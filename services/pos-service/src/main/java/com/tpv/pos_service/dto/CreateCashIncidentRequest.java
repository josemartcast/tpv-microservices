package com.tpv.pos_service.dto;

import com.tpv.pos_service.domain.CashIncidentDirection;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateCashIncidentRequest(
        @NotNull CashIncidentDirection direction,
        @Min(1) int amountCents,
        String note
) {
}
