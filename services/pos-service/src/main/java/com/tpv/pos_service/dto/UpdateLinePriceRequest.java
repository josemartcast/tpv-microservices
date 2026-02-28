package com.tpv.pos_service.dto;

import jakarta.validation.constraints.Min;

public record UpdateLinePriceRequest(
    @Min(0) int priceCents
) {}
