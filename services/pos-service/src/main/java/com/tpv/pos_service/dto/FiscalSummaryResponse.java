package com.tpv.pos_service.dto;

public record FiscalSummaryResponse(
    long cashSessionId,
    int paidTicketsCount,
    int cancelledTicketsCount,

    int grossSalesCents,
    int netSalesCents,
    int vatSalesCents,

    int cashPaymentsCents,
    int cardPaymentsCents,
    int bizumPaymentsCents
) {}
