package com.tpv.pos_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/pos/health").permitAll()
                //categories
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/categories/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/categories").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/pos/categories/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/pos/categories/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/pos/categories/**").hasRole("ADMIN")
                //products
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/products/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/products/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/pos/products/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/pos/products/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/pos/products/**").hasRole("ADMIN")
                //tickets        
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/tickets/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/tickets/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/pos/tickets/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/pos/tickets/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/tickets/*/pay").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/tickets/*/cancel").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/tickets/*/payments").hasRole("ADMIN")
                //paymentSummary
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/tickets/*/payment-summary")
                .hasAnyRole("USER", "ADMIN")
                //cash session
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/cash-sessions/current")
                .hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/cash-sessions/open")
                .hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/cash-sessions/*/close")
                .hasRole("ADMIN")
                .anyRequest().denyAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(new JwtAuthConverter()))
                );

        return http.build();
    }
}
