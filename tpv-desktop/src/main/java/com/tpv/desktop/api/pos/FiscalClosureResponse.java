package com.tpv.desktop.api.pos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FiscalClosureResponse(
    long cashSessionId,
    int openingCashCents,
    int expectedCashCents,
    int cashPaymentsCents,
    int grossSalesCents,
    int netSalesCents,
    int vatSalesCents
) {}
