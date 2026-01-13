package com.tpv.pos_service.dto;

import com.tpv.pos_service.domain.PaymentMethod;
import java.time.Instant;

public record PaymentResponse(
    Long id,
    PaymentMethod method,
    int amountCents,
    Instant createdAt
) {}
