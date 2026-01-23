package com.tpv.pos_service.dto;

import java.time.Instant;
import java.util.List;
import com.tpv.pos_service.domain.CashSessionStatus;
import com.tpv.pos_service.domain.PaymentMethod;

public record CashSessionFiscalCloseResponse(
    long cashSessionId,
    CashSessionStatus status,
    Instant openedAt,
    Instant closedAt,

    int grossCents,      // total ventas (bruto)
    int netCents,        // base imponible
    int vatCents,        // iva total

    List<VatBreakdown> vatBreakdown,
    List<PaymentBreakdown> paymentBreakdown,

    int paidTicketsCount,
    int cancelledTicketsCount,
    int openTicketsCount
) {
  public record VatBreakdown(int vatRateBps, int netCents, int vatCents, int grossCents) {}
  public record PaymentBreakdown(PaymentMethod method, int amountCents) {}
}
