package com.tpv.auth_service.controller.dto;

import jakarta.validation.constraints.NotNull;

public record AdminUserSetActiveRequest(
        @NotNull Boolean active
) {
}

