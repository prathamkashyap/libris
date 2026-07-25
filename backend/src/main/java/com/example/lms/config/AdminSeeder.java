package com.example.lms.config;

import com.example.lms.entity.Account;
import com.example.lms.entity.Role;
import com.example.lms.repository.AccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminSeeder {

    @Bean
    public CommandLineRunner seedAdmin(AccountRepository accountRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (accountRepository.findByUsername("admin").isEmpty()) {
                Account admin = new Account();
                admin.setUsername("admin");
                admin.setPasswordHash(passwordEncoder.encode("ChangeMe123!"));
                admin.setRole(Role.ADMIN);
                accountRepository.save(admin);
            }
        };
    }
}
