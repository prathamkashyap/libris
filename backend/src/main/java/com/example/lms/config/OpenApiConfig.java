package com.example.lms.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Libris API")
                        .version("1.0.0")
                        .description("REST API for the Library Management System. Supports books, magazines, newspapers, students, librarians, borrow/return workflows, audit logging, analytics, and reports.")
                        .contact(new Contact()
                                .name("Libris")
                                .email("admin@libris.app")));
    }
}
