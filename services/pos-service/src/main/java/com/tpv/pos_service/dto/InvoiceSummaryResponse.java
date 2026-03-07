package com.tpv.pos_service.dto;

import java.time.Instant;

public record InvoiceSummaryResponse(
        long id,
        String invoiceNumber,
        Instant issuedAt,
        String issuedBy,
        long ticketId,
        Integer tableNumber,
        long customerId,
        String customerDisplayName,
        String customerTaxId,
        int totalGrossCents
) {
}
