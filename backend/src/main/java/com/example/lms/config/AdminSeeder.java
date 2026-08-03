package com.example.lms.config;

import com.example.lms.entity.Account;
import com.example.lms.entity.Role;
import com.example.lms.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminSeeder {

    @Bean
    public CommandLineRunner seedAdmin(AccountRepository accountRepository, PasswordEncoder passwordEncoder,
                                       @Value("${lms.admin.username}") String adminUsername,
                                       @Value("${lms.admin.password}") String adminPassword) {
        return args -> {
            if (accountRepository.findByUsername(adminUsername).isEmpty()) {
                if (adminPassword == null || adminPassword.isBlank()) {
                    throw new IllegalStateException("LMS_ADMIN_PASSWORD environment variable is required because no administrator account exists. Set it before starting the application.");
                }
                Account admin = new Account();
                admin.setUsername(adminUsername);
                admin.setPasswordHash(passwordEncoder.encode(adminPassword));
                admin.setRole(Role.ADMIN);
                accountRepository.save(admin);
            }
        };
    }
}
