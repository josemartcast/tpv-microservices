package com.tpv.pos_service.dto;

import jakarta.validation.constraints.NotBlank;

public record AutoPrintClaimRequest(
        @NotBlank String destination,
        @NotBlank String printJobId
) {
}
