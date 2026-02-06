package com.tpv.desktop.core;

public final class AuthStore {
  private static String token; // JWT

  private AuthStore() {}

  public static void setToken(String jwt) { token = jwt; }
  public static String getToken() { return token; }
  public static boolean isLoggedIn() { return token != null && !token.isBlank(); }
  public static void clear() { token = null; }
}

