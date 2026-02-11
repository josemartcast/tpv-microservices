package com.tpv.pos_service.dto;

import com.tpv.pos_service.domain.PaymentMethod;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreatePaymentRequest(
    @NotNull PaymentMethod method,
    @Min(1) int amountCents
) {}
