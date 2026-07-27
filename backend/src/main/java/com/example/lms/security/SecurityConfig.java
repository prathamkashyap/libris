package com.example.lms.security;

import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration @EnableWebSecurity
public class SecurityConfig {
    private final AccountUserDetailsService users; private final PasswordEncoder passwords;
    private final RestAuthenticationEntryPoint authenticationEntryPoint; private final RestAccessDeniedHandler accessDeniedHandler;
    public SecurityConfig(AccountUserDetailsService users, PasswordEncoder passwords, RestAuthenticationEntryPoint entryPoint, RestAccessDeniedHandler deniedHandler) { this.users = users; this.passwords = passwords; this.authenticationEntryPoint = entryPoint; this.accessDeniedHandler = deniedHandler; }
    @Bean AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception { return configuration.getAuthenticationManager(); }
    @Bean DaoAuthenticationProvider provider() { var provider = new DaoAuthenticationProvider(); provider.setUserDetailsService(users); provider.setPasswordEncoder(passwords); return provider; }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();

        return http.csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokenRepository)
                .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(authenticationEntryPoint).accessDeniedHandler(accessDeniedHandler))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/login.html", "/styles.css", "/css/**", "/js/**", "/components/**", "/assets/**", "/api/auth/login", "/api/auth/csrf", "/login.html", "/register.html").permitAll()
                .requestMatchers("/api/auth/logout", "/api/auth/me", "/api/profile").authenticated()
                .requestMatchers("/api/student/**").hasAnyRole("ADMIN", "LIBRARIAN", "STUDENT")
                .requestMatchers(HttpMethod.GET, "/api/books/**", "/api/magazines/**", "/api/newspapers/**").authenticated()
                .requestMatchers("/api/librarians/**").hasRole("ADMIN")
                .requestMatchers("/api/analytics/**", "/api/audit/**", "/api/reports/**").hasAnyRole("ADMIN", "LIBRARIAN")
                .requestMatchers("/api/books/**", "/api/students/**", "/api/borrow-records/**", "/api/dashboard").hasAnyRole("ADMIN", "LIBRARIAN")
                .anyRequest().authenticated())
            .oauth2Login(oauth2 -> oauth2.defaultSuccessUrl("/"))
            .userDetailsService(users).build();
    }
}
