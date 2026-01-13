// PaymentSummaryResponse.java
package com.tpv.pos_service.dto;

public record PaymentSummaryResponse(
        long ticketId,
        int ticketTotalCents,
        int paidCents,
        int pendingCents
) {}
