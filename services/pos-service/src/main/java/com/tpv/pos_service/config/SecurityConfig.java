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
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/categories/**").hasAnyRole("CAMARERO", "CAJERO", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/categories").hasAnyRole("ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/pos/categories/**").hasAnyRole("ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/pos/categories/**").hasAnyRole("ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/pos/categories/**").hasAnyRole("ENCARGADO", "ADMIN")
                // business profile
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/business-profile").hasAnyRole("CAMARERO", "CAJERO", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/pos/business-profile").hasAnyRole("ENCARGADO", "ADMIN")
                // customers (fiscal billing data)
                .requestMatchers(RegexRequestMatcher.regexMatcher("/api/v1/pos/customers(/.*)?")).hasAnyRole("CAJERO", "ENCARGADO", "ADMIN")
                //products
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/products/**").hasAnyRole("CAMARERO", "CAJERO", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/products").hasAnyRole("ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/products/**").hasAnyRole("ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/pos/products/**").hasAnyRole("ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/pos/products/**").hasAnyRole("ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/pos/products/**").hasAnyRole("ENCARGADO", "ADMIN")
                //tickets        
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/tickets/*/invoice").hasAnyRole("CAJERO", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/tickets/*/invoice").hasAnyRole("CAJERO", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/tickets/**").hasAnyRole("CAMARERO", "CAJERO", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/tickets").hasAnyRole("CAMARERO", "ENCARGADO", "ADMIN")
                .requestMatchers(RegexRequestMatcher.regexMatcher(HttpMethod.POST, "/api/v1/pos/tickets/?$")).hasAnyRole("CAMARERO", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/tickets/*/pay").denyAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/tickets/*/cancel").hasAnyRole("ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/tickets/*/payments").hasAnyRole("CAMARERO", "CAJERO", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/tickets/*/refunds").hasAnyRole("ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/tickets/*/reopen-paid").hasAnyRole("ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/tickets/**").hasAnyRole("CAMARERO", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/pos/tickets/**").hasAnyRole("CAMARERO", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/pos/tickets/**").hasAnyRole("CAMARERO", "ENCARGADO", "ADMIN")
                //fiscalSummary
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/cash-sessions/*/fiscal-summary")
                .hasAnyRole("CAJERO", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/cash-sessions/*/fiscal-closure")
                .hasAnyRole("CAJERO", "ENCARGADO", "ADMIN")
                //paymentSummary
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/tickets/*/payment-summary")
                .hasAnyRole("CAJERO", "ENCARGADO", "ADMIN")
                //cash session
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/cash-sessions/current")
                .hasAnyRole("CAJERO", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/cash-sessions/open")
                .hasAnyRole("CAJERO", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/cash-sessions/*/close")
                .hasAnyRole("CAJERO", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/cash-sessions/*/close-summary")
                .hasAnyRole("CAJERO", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/cash-sessions/*/incidents")
                .hasAnyRole("CAJERO", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/cash-sessions/*/open-tickets")
                .hasAnyRole("CAJERO", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/cash-sessions/*/resolve-open-tickets")
                .hasAnyRole("CAJERO", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/cash-sessions/*/incidents")
                .hasAnyRole("CAJERO", "ENCARGADO", "ADMIN")
                // audit
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/audit/events")
                .hasAnyRole("ENCARGADO", "ADMIN")
                // fiscal exercises (annual)
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/fiscal-exercises/**")
                .hasAnyRole("CAJERO", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/fiscal-exercises/open")
                .hasAnyRole("ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/fiscal-exercises/*/close")
                .hasAnyRole("ENCARGADO", "ADMIN")
                // admin tools
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/admin/seed-catalog")
                .hasAnyRole("ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/admin/salons")
                .hasAnyRole("ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/admin/salons/**")
                .hasAnyRole("ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/admin/salons")
                .hasAnyRole("ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/pos/admin/salons/**")
                .hasAnyRole("ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/pos/admin/salons/**")
                .hasAnyRole("ENCARGADO", "ADMIN")
                // salon
                .requestMatchers(HttpMethod.GET, "/api/v1/pos/salon/tables")
                .hasAnyRole("CAMARERO", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/salon/tables/*/open-ticket")
                .hasAnyRole("CAMARERO", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/salon/tables/*/lock")
                .hasAnyRole("CAMARERO", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/salon/tables/*/heartbeat")
                .hasAnyRole("CAMARERO", "ENCARGADO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pos/salon/tables/*/unlock")
                .hasAnyRole("CAMARERO", "ENCARGADO", "ADMIN")
                .anyRequest().denyAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(new JwtAuthConverter()))
                );

        return http.build();
    }
}

