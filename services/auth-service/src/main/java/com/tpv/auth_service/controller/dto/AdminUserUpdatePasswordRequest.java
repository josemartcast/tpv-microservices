package com.tpv.auth_service.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminUserUpdatePasswordRequest(
        @NotBlank String password
) {
}

