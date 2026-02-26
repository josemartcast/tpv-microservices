package com.tpv.pos_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReopenPaidTicketRequest(
        @NotBlank @Size(min = 6, max = 180) String reason
) {
}

