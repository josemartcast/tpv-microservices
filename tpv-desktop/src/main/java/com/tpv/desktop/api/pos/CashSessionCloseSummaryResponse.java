package com.tpv.desktop.api.pos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CashSessionCloseSummaryResponse(
        long cashSessionId,
        Instant openedAt,
        Instant closedAt,
        int openingCashCents,
        int expectedCashCents,
        Integer closingCashCents,
        Integer cashDifferenceCents,
        int cashPaymentsNetCents,
        int incidentsInCents,
        int incidentsOutCents,
        int incidentsNetCents,
        FiscalSummaryResponse fiscal
) {
}
