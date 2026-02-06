package com.tpv.desktop.api.pos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TicketSummaryResponse(
    long id,
    String status,
    int totalCents,
    int paidCents,
    int remainingCents,
    Instant createdAt,
    List<TicketLineSummary> lines,
    List<PaymentSummary> payments
) {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record TicketLineSummary(
      long lineId,
      long productId,
      String productName,
      int unitPriceCents,
      int qty,
      int lineTotalCents
  ) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record PaymentSummary(
      long id,
      String method,
      int amountCents,
      Instant createdAt
  ) {}
}
