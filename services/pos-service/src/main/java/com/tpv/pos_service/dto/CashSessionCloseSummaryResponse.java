package com.tpv.pos_service.dto;

import java.time.Instant;

public record CashSessionCloseSummaryResponse(
        long cashSessionId,
        Instant openedAt,
        Instant closedAt,
        int openingCashCents,
        int expectedCashCents,
        int closingCashCents,
        int cashDifferenceCents,
        FiscalSummaryResponse fiscal
) {}

