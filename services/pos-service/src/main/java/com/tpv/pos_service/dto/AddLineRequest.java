package com.tpv.pos_service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddLineRequest(
    @NotNull Long productId,
    @Min(1) int qty
) {}
