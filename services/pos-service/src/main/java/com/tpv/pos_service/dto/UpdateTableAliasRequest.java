package com.tpv.pos_service.dto;

import jakarta.validation.constraints.Size;

public record UpdateTableAliasRequest(
        @Size(max = 80) String alias
) {
}

