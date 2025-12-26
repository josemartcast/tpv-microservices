
package com.tpv.auth_service.controller;

import com.tpv.auth_service.controller.dto.LoginRequest;
import com.tpv.auth_service.controller.dto.LoginResponse;
import com.tpv.auth_service.service.JwtService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.tpv.auth_service.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final JwtService jwtService;
    private final long expiresInSeconds;
    
    public AuthController ( AuthService authService, JwtService jwtService, @Value ("${app.jwt.expiration-minutes}")long expirationMinutes){
        this.authService = authService;
        this.expiresInSeconds = expirationMinutes * 60;
        this.jwtService = jwtService;
    }
    
    @PostMapping("/login")
    public LoginResponse login (@Valid @RequestBody LoginRequest req){
        
        var user = authService.authenticate(req.username(), req.password());
        var roles = List.of(user.getRole().name());
        
        String token = jwtService.generateToken(user.getId(),user.getUsername(), roles);
        return new LoginResponse(token,expiresInSeconds, roles);
    }
@GetMapping("/me")
public Map<String, Object> me(Authentication auth) {
    return Map.of(
        "username", auth.getName(),
        "authorities", auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .collect(Collectors.toList())
    );
}
    
}
