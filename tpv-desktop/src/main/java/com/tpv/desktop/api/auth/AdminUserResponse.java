package com.tpv.desktop.api.auth;

public record AdminUserResponse(
        long id,
        String username,
        String role,
        boolean active
) {
}

