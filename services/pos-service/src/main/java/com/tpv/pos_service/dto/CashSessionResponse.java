package com.tpv.pos_service.dto;

import com.tpv.pos_service.domain.CashSessionStatus;
import java.time.Instant;

public record CashSessionResponse(
    long id,
    CashSessionStatus status,
    int openingCashCents,
    Integer closingCashCents,
    Instant openedAt,
    Instant closedAt,
    String openedBy,
    String closedBy,
    String note
) {}
