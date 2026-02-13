package com.tpv.auth_service.controller;

import com.tpv.auth_service.controller.dto.AdminUserCreateRequest;
import com.tpv.auth_service.controller.dto.AdminUserResponse;
import com.tpv.auth_service.controller.dto.AdminUserSetActiveRequest;
import com.tpv.auth_service.controller.dto.AdminUserUpdatePasswordRequest;
import com.tpv.auth_service.controller.dto.AdminUserUpdateRoleRequest;
import com.tpv.auth_service.domain.User;
import com.tpv.auth_service.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/admin/users")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<AdminUserResponse> listUsers() {
        return userService.listUsers().stream().map(this::toResponse).toList();
    }

    @PostMapping
    public AdminUserResponse createUser(@Valid @RequestBody AdminUserCreateRequest request) {
        return toResponse(userService.createUser(request.username(), request.password(), request.role()));
    }

    @PatchMapping("/{id}/role")
    public AdminUserResponse updateRole(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserUpdateRoleRequest request
    ) {
        return toResponse(userService.updateRole(id, request.role()));
    }

    @PatchMapping("/{id}/password")
    public AdminUserResponse updatePassword(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserUpdatePasswordRequest request
    ) {
        return toResponse(userService.updatePassword(id, request.password()));
    }

    @PatchMapping("/{id}/active")
    public AdminUserResponse setActive(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserSetActiveRequest request
    ) {
        return toResponse(userService.setActive(id, request.active()));
    }

    @DeleteMapping("/{id}")
    public AdminUserResponse deleteUser(@PathVariable Long id, Authentication authentication) {
        String currentUsername = authentication == null ? null : authentication.getName();
        return toResponse(userService.deleteUser(id, currentUsername));
    }

    @PatchMapping("/{id}/deactivate")
    public AdminUserResponse deactivate(@PathVariable Long id) {
        return toResponse(userService.setActive(id, false));
    }

    private AdminUserResponse toResponse(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole().name(),
                user.isActive()
        );
    }
}
