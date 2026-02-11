package com.tpv.pos_service.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

public final class ActorResolver {

    private ActorResolver() {
    }

    public static String usernameFrom(Authentication auth) {
        if (auth == null) {
            return "anonymous";
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof Jwt jwt) {
            String username = jwt.getClaimAsString("username");
            if (username != null && !username.isBlank()) {
                return username;
            }
            return jwt.getSubject() == null || jwt.getSubject().isBlank() ? "anonymous" : jwt.getSubject();
        }
        String name = auth.getName();
        return name == null || name.isBlank() ? "anonymous" : name;
    }

    public static String terminalFromHeader(String terminalIdHeader) {
        if (terminalIdHeader == null || terminalIdHeader.isBlank()) {
            return "UNKNOWN";
        }
        return terminalIdHeader.trim();
    }
}
