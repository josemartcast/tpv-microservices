package com.tpv.auth_service.controller.dto;

import com.tpv.auth_service.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminUserCreateRequest(
        @NotBlank String username,
        @NotBlank String password,
        @NotNull Role role
) {
}

