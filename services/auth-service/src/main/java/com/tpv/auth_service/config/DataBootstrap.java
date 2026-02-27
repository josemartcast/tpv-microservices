
package com.tpv.auth_service.config;

import com.tpv.auth_service.domain.Role;
import com.tpv.auth_service.domain.User;
import com.tpv.auth_service.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataBootstrap {
   
    @Bean
    CommandLineRunner initAdmin (UserRepository repo, PasswordEncoder encoder){
        return args -> {
            repo.findByUsername("admin").orElseGet(() -> repo.save(new User("admin", encoder.encode("admin123"), Role.ADMIN)));
            migrateLegacyUserRole(repo);
        };
    }

    private static void migrateLegacyUserRole(UserRepository repo) {
        var legacyUsers = repo.findAllByRole(Role.USER);
        if (legacyUsers.isEmpty()) {
            return;
        }
        for (User user : legacyUsers) {
            user.setRole(Role.CAMARERO);
        }
        repo.saveAll(legacyUsers);
    }
}
