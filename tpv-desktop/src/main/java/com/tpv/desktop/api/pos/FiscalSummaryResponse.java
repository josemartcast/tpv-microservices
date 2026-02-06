package com.tpv.desktop.api.pos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
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
