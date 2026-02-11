package com.tpv.pos_service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record MoveTableRequest(
        @NotNull
        @Min(1)
        @Max(200)
        Integer tableNumber
) {
}

