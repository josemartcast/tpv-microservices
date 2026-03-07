package com.tpv.desktop.api.pos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
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
