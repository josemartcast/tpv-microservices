package com.tpv.desktop.api.pos;

public record PaymentSummaryResponse(
        long ticketId,
        int ticketTotalCents,
        int paidCents,
        int pendingCents
) {}
