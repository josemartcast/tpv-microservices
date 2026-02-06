package com.tpv.desktop.api.pos;

import java.time.Instant;

public record CashSessionResponse(
    long id,
    String status,               
    int openingCashCents,
    int expectedCashCents,
    Integer closingCashCents,
    Integer differenceCents,
    Instant openedAt,
    Instant closedAt,
    String openedBy,
    String closedBy,
    String note
) {}
