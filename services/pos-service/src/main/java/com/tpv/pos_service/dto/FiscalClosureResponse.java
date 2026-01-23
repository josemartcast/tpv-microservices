package com.tpv.pos_service.dto;

import java.time.Instant;

public record FiscalClosureResponse(
    long cashSessionId,
    Instant createdAt,

    int paidTicketsCount,
    int cancelledTicketsCount,

    int grossSalesCents,
    int netSalesCents,
    int vatSalesCents,

    int cashPaymentsCents,
    int cardPaymentsCents,
    int bizumPaymentsCents
) {}
