package com.tpv.desktop.api.auth;

public record AdminUserCreateRequest(
        String username,
        String password,
        String role
) {
}

