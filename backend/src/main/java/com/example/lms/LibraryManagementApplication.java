package com.example.lms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class LibraryManagementApplication {
  public static void main(String[] args) {
    SpringApplication.run(LibraryManagementApplication.class, args);
  }
}
