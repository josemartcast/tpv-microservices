package com.tpv.pos_service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ApplyDiscountRequest(
        @Min(0) @Max(100) Integer percent,
        @Min(0) Integer amountCents
) {
}

