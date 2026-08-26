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

@Configuration
@EnableWebSecurity
public class SecurityConfig {
  private final AccountUserDetailsService users;
  private final PasswordEncoder passwords;
  private final RestAuthenticationEntryPoint authenticationEntryPoint;
  private final RestAccessDeniedHandler accessDeniedHandler;
  private final CustomOidcUserService oidcUserService;

  public SecurityConfig(
      AccountUserDetailsService users,
      PasswordEncoder passwords,
      RestAuthenticationEntryPoint entryPoint,
      RestAccessDeniedHandler deniedHandler,
      org.springframework.beans.factory.ObjectProvider<CustomOidcUserService> oidcUserService) {
    this.users = users;
    this.passwords = passwords;
    this.authenticationEntryPoint = entryPoint;
    this.accessDeniedHandler = deniedHandler;
    this.oidcUserService = oidcUserService.getIfAvailable();
  }

  @Bean
  AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
      throws Exception {
    return configuration.getAuthenticationManager();
  }

  @Bean
  DaoAuthenticationProvider provider() {
    var provider = new DaoAuthenticationProvider();
    provider.setUserDetailsService(users);
    provider.setPasswordEncoder(passwords);
    return provider;
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      org.springframework.beans.factory.ObjectProvider<
              org.springframework.security.oauth2.client.registration.ClientRegistrationRepository>
          clientRegistrationRepository)
      throws Exception {
    CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();

    http.csrf(
            csrf ->
                csrf.csrfTokenRepository(csrfTokenRepository)
                    .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/login.html",
                        "/register.html",
                        "/styles.css",
                        "/css/**",
                        "/js/**",
                        "/components/**",
                        "/assets/**",
                        "/favicon.svg",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/actuator/**",
                        "/api/auth/login",
                        "/api/auth/csrf",
                        "/api/auth/register")
                    .permitAll()
                    .requestMatchers("/api/auth/logout", "/api/auth/me", "/api/profile")
                    .authenticated()
                    .requestMatchers("/api/student/**")
                    .hasAnyRole("ADMIN", "LIBRARIAN", "STUDENT")
                    .requestMatchers(
                        HttpMethod.GET, "/api/books/**", "/api/magazines/**", "/api/newspapers/**")
                    .authenticated()
                    .requestMatchers("/api/librarians/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/analytics/**", "/api/audit/**", "/api/reports/**")
                    .hasAnyRole("ADMIN", "LIBRARIAN")
                    .requestMatchers("/api/borrow-records/my")
                    .hasAnyRole("ADMIN", "LIBRARIAN", "STUDENT")
                    .requestMatchers(
                        "/api/books/**",
                        "/api/magazines/**",
                        "/api/newspapers/**",
                        "/api/students/**",
                        "/api/borrow-records/**",
                        "/api/dashboard")
                    .hasAnyRole("ADMIN", "LIBRARIAN")
                    .anyRequest()
                    .authenticated());

    org.springframework.security.oauth2.client.registration.ClientRegistrationRepository repo =
        clientRegistrationRepository.getIfAvailable();
    if (repo != null) {
      http.oauth2Login(
          oauth2 -> {
            oauth2.defaultSuccessUrl("/");
            if (oidcUserService != null) {
              oauth2.userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService));
            }
          });
    }

    http.userDetailsService(users);
    return http.build();
  }
}
