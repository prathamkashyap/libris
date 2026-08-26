package com.example.lms.config;

import com.example.lms.entity.Account;
import com.example.lms.entity.Role;
import com.example.lms.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminSeeder {

  private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

  @Bean
  public CommandLineRunner seedAdmin(
      AccountRepository accountRepository,
      PasswordEncoder passwordEncoder,
      @Value("${lms.admin.username}") String adminUsername,
      @Value("${lms.admin.password}") String adminPassword) {

    return args -> {
      log.info("AdminSeeder: Starting");

      if (adminPassword == null || adminPassword.isBlank()) {
        throw new IllegalStateException("LMS_ADMIN_PASSWORD environment variable is required.");
      }

      Account admin = accountRepository.findByUsername(adminUsername).orElseGet(Account::new);

      boolean isNew = admin.getId() == null;
      log.info("Admin account exists: {}", !isNew);

      admin.setUsername(adminUsername);

      if (!isNew) {
        boolean matches =
            admin.getPasswordHash() != null
                && passwordEncoder.matches(adminPassword, admin.getPasswordHash());
        if (!matches) {
          log.info("Updating admin password");
          admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        } else {
          log.info("Admin password already up to date");
        }
      } else {
        log.info("Creating new admin account");
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
      }

      admin.setRole(Role.ADMIN);
      admin.setEnabled(true);

      accountRepository.save(admin);
      log.info("AdminSeeder: Completed");
    };
  }
}
