package com.tpv.pos_service.dto;

import jakarta.validation.constraints.Min;

public record ConsumeLineForPaymentRequest(
        @Min(1) int qty
) {}

