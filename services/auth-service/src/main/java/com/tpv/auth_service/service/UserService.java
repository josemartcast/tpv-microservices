package com.tpv.auth_service.service;

import com.tpv.auth_service.domain.User;
import com.tpv.auth_service.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User createUser(String username, String passwordHash, String role) {
        User user = new User(username, passwordHash, role);
        return userRepository.save(user);
    }
}
