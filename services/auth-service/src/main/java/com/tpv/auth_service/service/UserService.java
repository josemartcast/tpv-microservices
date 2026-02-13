package com.tpv.auth_service.service;

import com.tpv.auth_service.domain.Role;
import com.tpv.auth_service.domain.User;
import com.tpv.auth_service.repository.UserRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<User> listUsers() {
        return userRepository.findAll()
                .stream()
                .sorted((a, b) -> a.getUsername().compareToIgnoreCase(b.getUsername()))
                .toList();
    }

    @Transactional
    public User createUser(String username, String rawPassword, Role role) {
        String normalizedUsername = normalizeUsername(username);
        validatePassword(rawPassword);
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role is required");
        }
        if (userRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists: " + normalizedUsername);
        }

        User user = new User(normalizedUsername, passwordEncoder.encode(rawPassword), role);
        return userRepository.save(user);
    }

    @Transactional
    public User updateRole(Long id, Role role) {
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role is required");
        }
        User user = getRequiredUser(id);
        user.setRole(role);
        return user;
    }

    @Transactional
    public User updatePassword(Long id, String rawPassword) {
        validatePassword(rawPassword);
        User user = getRequiredUser(id);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        return user;
    }

    @Transactional
    public User setActive(Long id, boolean active) {
        User user = getRequiredUser(id);
        if (!active && user.isActive() && user.getRole() == Role.ADMIN) {
            ensureAtLeastOneActiveAdminRemains();
        }
        if (active) {
            user.activate();
        } else {
            user.deactivate();
        }
        return user;
    }

    @Transactional
    public User deleteUser(Long id, String currentUsername) {
        User user = getRequiredUser(id);

        if (currentUsername != null && user.getUsername().equalsIgnoreCase(currentUsername.trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot delete current authenticated user");
        }
        if (user.isActive() && user.getRole() == Role.ADMIN) {
            ensureAtLeastOneActiveAdminRemains();
        }

        userRepository.delete(user);
        return user;
    }

    private User getRequiredUser(Long id) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid user id");
        }
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id));
    }

    private static String normalizeUsername(String username) {
        if (username == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required");
        }
        String normalized = username.trim();
        if (!normalized.matches("[A-Za-z0-9._@\\-]{3,50}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Username must be 3-50 chars and contain only letters, numbers, dot, underscore, dash or @");
        }
        return normalized;
    }

    private static void validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 6 || rawPassword.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password length must be 6-100 chars");
        }
    }

    private void ensureAtLeastOneActiveAdminRemains() {
        long activeAdmins = userRepository.countByRoleAndActiveTrue(Role.ADMIN);
        if (activeAdmins <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "At least one active ADMIN is required");
        }
    }
}
