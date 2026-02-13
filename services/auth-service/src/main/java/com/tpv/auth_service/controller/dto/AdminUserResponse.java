package com.tpv.auth_service.controller.dto;

public record AdminUserResponse(
        long id,
        String username,
        String role,
        boolean active
) {
}

