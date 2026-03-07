package com.tpv.desktop.api.pos;

import com.tpv.desktop.api.ApiClient;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

public final class InvoiceApi {
    private InvoiceApi() {}

    public static InvoiceSummaryResponse[] list(
            String invoiceNumber,
            String customer,
            LocalDate issuedFrom,
            LocalDate issuedTo,
            Integer limit
    ) throws Exception {
        StringBuilder path = new StringBuilder("/api/v1/pos/invoices");
        String sep = "?";
        if (invoiceNumber != null && !invoiceNumber.isBlank()) {
            path.append(sep).append("invoiceNumber=").append(url(invoiceNumber.trim()));
            sep = "&";
        }
        if (customer != null && !customer.isBlank()) {
            path.append(sep).append("customer=").append(url(customer.trim()));
            sep = "&";
        }
        if (issuedFrom != null) {
            path.append(sep).append("issuedFrom=").append(issuedFrom);
            sep = "&";
        }
        if (issuedTo != null) {
            path.append(sep).append("issuedTo=").append(issuedTo);
            sep = "&";
        }
        if (limit != null && limit > 0) {
            path.append(sep).append("limit=").append(limit);
        }
        return ApiClient.get(path.toString(), InvoiceSummaryResponse[].class);
    }

    private static String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
