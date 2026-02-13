package com.tpv.desktop.api.auth;

import com.tpv.desktop.api.ApiClient;
import java.util.Arrays;
import java.util.List;

public final class AuthApi {
  private AuthApi() {}

  public static LoginResponse login(String user, String pass) throws Exception {
    return ApiClient.post("/api/v1/auth/login", new LoginRequest(user, pass), LoginResponse.class);
  }

  public static List<AdminUserResponse> listUsers() throws Exception {
    AdminUserResponse[] users = ApiClient.get("/api/v1/auth/admin/users", AdminUserResponse[].class);
    return Arrays.asList(users == null ? new AdminUserResponse[0] : users);
  }

  public static AdminUserResponse createUser(String username, String password, String role) throws Exception {
    return ApiClient.post(
            "/api/v1/auth/admin/users",
            new AdminUserCreateRequest(username, password, role),
            AdminUserResponse.class
    );
  }

  public static AdminUserResponse updateRole(long userId, String role) throws Exception {
    return ApiClient.patch(
            "/api/v1/auth/admin/users/" + userId + "/role",
            new AdminUserUpdateRoleRequest(role),
            AdminUserResponse.class
    );
  }

  public static AdminUserResponse updatePassword(long userId, String password) throws Exception {
    return ApiClient.patch(
            "/api/v1/auth/admin/users/" + userId + "/password",
            new AdminUserUpdatePasswordRequest(password),
            AdminUserResponse.class
    );
  }

  public static AdminUserResponse setActive(long userId, boolean active) throws Exception {
    return ApiClient.patch(
            "/api/v1/auth/admin/users/" + userId + "/active",
            new AdminUserSetActiveRequest(active),
            AdminUserResponse.class
    );
  }

  public static AdminUserResponse deleteUser(long userId) throws Exception {
    return ApiClient.delete(
            "/api/v1/auth/admin/users/" + userId,
            AdminUserResponse.class
    );
  }
}
