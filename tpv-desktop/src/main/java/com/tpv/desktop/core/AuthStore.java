package com.tpv.desktop.core;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

public final class AuthStore {
  private static String token; // JWT

  private AuthStore() {}

  public static void setToken(String jwt) { token = jwt; }
  public static String getToken() { return token; }
  public static boolean isLoggedIn() { return token != null && !token.isBlank(); }
  public static boolean hasRole(String role) {
    if (role == null || role.isBlank() || token == null || token.isBlank()) {
      return false;
    }
    String[] parts = token.split("\\.");
    if (parts.length < 2) {
      return false;
    }
    try {
      String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
              .toUpperCase(Locale.ROOT);
      String target = role.toUpperCase(Locale.ROOT);
      return payload.contains("\"ROLES\":[\"" + target + "\"")
              || payload.contains("\"ROLES\":[\"ROLE_" + target + "\"")
              || payload.contains("\"ROLE_" + target + "\"");
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
  public static void clear() { token = null; }
}

