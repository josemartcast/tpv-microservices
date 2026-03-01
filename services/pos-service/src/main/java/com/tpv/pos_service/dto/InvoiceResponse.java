package com.tpv.pos_service.dto;

import java.time.Instant;
import java.util.List;

public record InvoiceResponse(
        long id,
        String invoiceNumber,
        Instant issuedAt,
        String issuedBy,
        long ticketId,
        Integer tableNumber,
        long customerId,
        String customerDisplayName,
        String customerLegalName,
        String customerTaxId,
        String customerAddress,
        String customerPostalCode,
        String customerCity,
        String customerProvince,
        String customerCountry,
        String customerPhone,
        String customerEmail,
        String businessName,
        String businessLegalName,
        String businessTaxId,
        String businessAddress,
        String businessPostalCode,
        String businessCity,
        String businessProvince,
        String businessCountry,
        String businessPhone,
        String businessEmail,
        int totalGrossCents,
        int totalNetCents,
        int totalVatCents,
        List<InvoiceLineResponse> lines
) {
}
