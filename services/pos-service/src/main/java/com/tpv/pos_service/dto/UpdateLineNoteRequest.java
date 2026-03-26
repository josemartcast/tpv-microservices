package com.tpv.pos_service.dto;

import jakarta.validation.constraints.Size;

public record UpdateLineNoteRequest(
        @Size(max = 255) String note
) {
}
