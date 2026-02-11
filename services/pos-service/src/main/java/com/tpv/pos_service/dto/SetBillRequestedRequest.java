package com.tpv.pos_service.dto;

import jakarta.validation.constraints.NotNull;

public record SetBillRequestedRequest(
        @NotNull Boolean requested
) {
}

