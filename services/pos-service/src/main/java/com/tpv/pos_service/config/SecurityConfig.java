package com.tpv.pos_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;

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
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/categories/**").hasAnyRole("USER", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/categories").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/pos/categories/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/pos/categories/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/pos/categories/**").hasRole("ADMIN")
                // business profile
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/business-profile").hasAnyRole("USER", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/pos/business-profile").hasRole("ADMIN")
                //products
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/products/**").hasAnyRole("USER", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/products").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/products/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/pos/products/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/pos/products/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/pos/products/**").hasRole("ADMIN")
                //tickets        
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/tickets/**").hasAnyRole("USER", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/tickets").hasAnyRole("USER", "ENCARGADO", "ADMIN")
                .requestMatchers(RegexRequestMatcher.regexMatcher(HttpMethod.POST, "/api/v1/pos/tickets/?$")).hasAnyRole("USER", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/tickets/*/pay").denyAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/tickets/*/cancel").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/tickets/*/payments").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/tickets/*/refunds").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/tickets/*/reopen-paid").hasAnyRole("ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/tickets/**").hasAnyRole("USER", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/pos/tickets/**").hasAnyRole("USER", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/pos/tickets/**").hasAnyRole("USER", "ENCARGADO", "ADMIN")
                //fiscalSummary
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/cash-sessions/*/fiscal-summary")
                .hasAnyRole("USER", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/cash-sessions/*/fiscal-closure")
                .hasAnyRole("USER", "ENCARGADO", "ADMIN")
                //paymentSummary
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/tickets/*/payment-summary")
                .hasAnyRole("USER", "ENCARGADO", "ADMIN")
                //cash session
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/cash-sessions/current")
                .hasAnyRole("USER", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/cash-sessions/open")
                .hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/cash-sessions/*/close")
                .hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/cash-sessions/*/close-summary")
                .hasAnyRole("USER", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/cash-sessions/*/incidents")
                .hasAnyRole("USER", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/cash-sessions/*/open-tickets")
                .hasAnyRole("USER", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/cash-sessions/*/resolve-open-tickets")
                .hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/cash-sessions/*/incidents")
                .hasRole("ADMIN")
                // audit
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/audit/events")
                .hasRole("ADMIN")
                // fiscal exercises (annual)
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/fiscal-exercises/**")
                .hasAnyRole("USER", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/fiscal-exercises/open")
                .hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/fiscal-exercises/*/close")
                .hasRole("ADMIN")
                // admin tools
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/admin/seed-catalog")
                .hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/admin/salons")
                .hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/admin/salons/**")
                .hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/admin/salons")
                .hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/pos/admin/salons/**")
                .hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/pos/admin/salons/**")
                .hasRole("ADMIN")
                // salon
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/salon/tables")
                .hasAnyRole("USER", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/salon/tables/*/open-ticket")
                .hasAnyRole("USER", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/salon/tables/*/lock")
                .hasAnyRole("USER", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/salon/tables/*/heartbeat")
                .hasAnyRole("USER", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/salon/tables/*/unlock")
                .hasAnyRole("USER", "ENCARGADO", "ADMIN")
                .anyRequest().denyAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(new JwtAuthConverter()))
                );

        return http.build();
    }
}

