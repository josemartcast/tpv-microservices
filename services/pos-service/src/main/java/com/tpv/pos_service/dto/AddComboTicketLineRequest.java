package com.tpv.pos_service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddComboTicketLineRequest(
        @NotNull Long baseProductId,
        @NotNull Long mixerProductId,
        @NotNull @Min(1) Integer qty
) {
}

