package com.tpv.desktop.api.auth;

import com.tpv.desktop.api.ApiClient;

public final class AuthApi {
  private AuthApi() {}

  public static LoginResponse login(String user, String pass) throws Exception {
    return ApiClient.post("/api/v1/auth/login", new LoginRequest(user, pass), LoginResponse.class);
  }
}
