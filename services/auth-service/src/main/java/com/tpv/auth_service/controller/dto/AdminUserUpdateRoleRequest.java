package com.tpv.auth_service.controller.dto;

import com.tpv.auth_service.domain.Role;
import jakarta.validation.constraints.NotNull;

public record AdminUserUpdateRoleRequest(
        @NotNull Role role
) {
}

