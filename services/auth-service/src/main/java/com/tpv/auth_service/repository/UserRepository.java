package com.tpv.auth_service.repository;

import com.tpv.auth_service.domain.User;
import com.tpv.auth_service.domain.Role;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);
    boolean existsByUsernameIgnoreCase(String username);
    long countByRoleAndActiveTrue(Role role);
    List<User> findAllByRole(Role role);
}
