package com.tpv.pos_service.dto;

import jakarta.validation.constraints.NotNull;

public record IssueInvoiceRequest(
        @NotNull(message = "customerId is required")
        Long customerId
) {
}
