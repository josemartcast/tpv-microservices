package com.tpv.pos_service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CreateTicketRequest(
        @Min(1) @Max(200) Integer tableNumber
) {
}
