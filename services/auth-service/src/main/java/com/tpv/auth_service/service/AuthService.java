
package com.tpv.auth_service.service;

import com.tpv.auth_service.domain.User;
import com.tpv.auth_service.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
   
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder){
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
    }
    
    public User authenticate(String username, String rawPassword){
        User user = userRepository.findByUsername(username).filter(User::isActive).orElseThrow(()-> new RuntimeException("Credenciales inválidas"));
        if(!passwordEncoder.matches(rawPassword, user.getPasswordHash())){
            throw new RuntimeException("Credenciales inválidas");
        }
        return user;
    }
}
