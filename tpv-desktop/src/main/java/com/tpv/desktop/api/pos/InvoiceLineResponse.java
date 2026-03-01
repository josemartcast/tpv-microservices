package com.tpv.desktop.api.pos;

public record InvoiceLineResponse(
        long id,
        long ticketLineId,
        String productName,
        int qty,
        int unitGrossCents,
        int lineGrossCents,
        int vatRateBps,
        int lineNetCents,
        int lineVatCents
) {
}
