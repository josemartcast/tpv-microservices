package com.tpv.pos_service.dto;

import jakarta.validation.constraints.NotBlank;

public record TableLockRequest(
        @NotBlank String terminalId
) {
}
