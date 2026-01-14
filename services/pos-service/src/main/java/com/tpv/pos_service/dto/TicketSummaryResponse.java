package com.tpv.pos_service.dto;

import java.time.Instant;
import java.util.List;

import com.tpv.pos_service.domain.TicketStatus;
import com.tpv.pos_service.domain.PaymentMethod;

public record TicketSummaryResponse(
        long ticketId,
        TicketStatus status,
        int totalCents,
        int paidCents,
        int remainingCents,
        Instant createdAt,
        List<TicketLineSummary> lines,
        List<PaymentSummary> payments
) {
    public record TicketLineSummary(
            long lineId,
            long productId,
            String productName,
            int unitPriceCents,
            int qty,
            int lineTotalCents
    ) {}

    public record PaymentSummary(
            long paymentId,
            PaymentMethod method,
            int amountCents,
            Instant createdAt
    ) {}
}