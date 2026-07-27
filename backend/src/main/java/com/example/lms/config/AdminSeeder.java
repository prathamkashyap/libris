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
    public CommandLineRunner seedAdmin(
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            @Value("${lms.admin.username}") String adminUsername,
            @Value("${lms.admin.password}") String adminPassword) {

        return args -> {

            System.out.println("=== AdminSeeder: Starting ===");
            System.out.println("Admin username from config: " + adminUsername);
            System.out.println("Admin password from config: " + (adminPassword != null ? "***" + adminPassword.substring(Math.max(0, adminPassword.length() - 4)) : "NULL"));

            if (adminPassword == null || adminPassword.isBlank()) {
                throw new IllegalStateException(
                    "LMS_ADMIN_PASSWORD environment variable is required."
                );
            }

            Account admin = accountRepository
                    .findByUsername(adminUsername)
                    .orElseGet(Account::new);

            boolean isNew = admin.getId() == null;
            System.out.println("Admin exists: " + !isNew);

            admin.setUsername(adminUsername);

            if (!isNew) {
                boolean matches = admin.getPasswordHash() != null &&
                        passwordEncoder.matches(adminPassword, admin.getPasswordHash());
                System.out.println("Current password matches: " + matches);
                if (!matches) {
                    System.out.println("Updating admin password...");
                    admin.setPasswordHash(passwordEncoder.encode(adminPassword));
                } else {
                    System.out.println("Password already up to date");
                }
            } else {
                System.out.println("Creating new admin with password");
                admin.setPasswordHash(passwordEncoder.encode(adminPassword));
            }

            admin.setRole(Role.ADMIN);
            admin.setEnabled(true);

            accountRepository.save(admin);
            System.out.println("=== AdminSeeder: Completed ===");
        };
    }
}
