package com.example.lms;

import org.springframework.boot.SpringApplication; import org.springframework.boot.SpringBootConfiguration; import org.springframework.boot.CommandLineRunner; import org.springframework.context.annotation.Bean; import org.springframework.data.jpa.repository.config.EnableJpaAuditing; import com.example.lms.entity.*; import com.example.lms.repository.AccountRepository; import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication @EnableJpaAuditing
public class LibraryManagementApplication {
    public static void main(String[] args) { SpringApplication.run(LibraryManagementApplication.class, args); }
    @Bean CommandLineRunner seedAdmin(AccountRepository accounts,PasswordEncoder passwords){return args->{if(!accounts.existsByUsername("admin")){var admin=new Account();admin.setUsername("admin");admin.setPasswordHash(passwords.encode("ChangeMe123!"));admin.setRole(Role.ADMIN);accounts.save(admin);}};}
}
