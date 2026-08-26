package com.example.lms.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
  @Bean
  public OpenAPI customOpenAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Libris — Library Management System API")
                .version("1.1.0")
                .description(
                    "Enterprise REST API for Libris Library Management System. Provides comprehensive endpoints for catalog inventory (books, magazines, newspapers), borrower management (students, librarians), circulation lifecycle (loans, returns, due dates), audit trail tracking, analytics aggregates, and CSV reports.")
                .contact(
                    new Contact()
                        .name("Libris Engineering Team")
                        .email("admin@libris.app")
                        .url("https://github.com/prathamkashyap/library-management-system"))
                .license(
                    new License().name("MIT License").url("https://opensource.org/licenses/MIT")))
        .addSecurityItem(new SecurityRequirement().addList("cookieAuth").addList("csrfToken"))
        .components(
            new Components()
                .addSecuritySchemes(
                    "cookieAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name("JSESSIONID")
                        .description("Session cookie obtained via POST /api/auth/login"))
                .addSecuritySchemes(
                    "csrfToken",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-XSRF-TOKEN")
                        .description(
                            "CSRF token obtained from XSRF-TOKEN cookie via GET /api/auth/csrf")));
  }
}
