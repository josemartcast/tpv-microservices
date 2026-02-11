package com.tpv.pos_service.dto;

import jakarta.validation.constraints.NotBlank;

public record SendComandaRequest(
        @NotBlank String destination
) {
}
