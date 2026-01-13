package com.tpv.pos_service.dto;

import com.tpv.pos_service.domain.PaymentMethod;

public record CreatePaymentRequest(
    PaymentMethod method,
    int amountCents
) {}
