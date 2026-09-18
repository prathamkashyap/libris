package com.example.lms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.lms.dto.LoginRequest;
import com.example.lms.repository.AccountRepository;
import com.example.lms.repository.StudentProfileRepository;
import com.example.lms.security.LoginAttemptTracker;
import com.example.lms.service.AuthService;
import com.example.lms.util.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock AuthenticationManager authenticationManager;
  @Mock AccountRepository accounts;
  @Mock StudentProfileRepository studentProfiles;
  @Mock ApplicationEventPublisher events;
  @Mock CurrentUser currentUser;
  @Mock PasswordEncoder passwordEncoder;
  @Mock LoginAttemptTracker loginAttempts;
  @Mock HttpServletRequest servletRequest;

  private AuthService service;

  @BeforeEach
  void setUp() {
    service =
        new AuthService(
            authenticationManager,
            accounts,
            studentProfiles,
            events,
            currentUser,
            passwordEncoder,
            loginAttempts);
  }

  @Test
  void auditFailureDoesNotReplaceBadCredentialsException() {
    when(loginAttempts.isLocked("user1")).thenReturn(false);
    when(authenticationManager.authenticate(any()))
        .thenThrow(new BadCredentialsException("Bad credentials"));
    doNothing().when(loginAttempts).recordFailure("user1");
    doThrow(new RuntimeException("Audit DB unavailable")).when(events).publishEvent(any());

    var request = new LoginRequest("user1", "wrong");

    assertThrows(
        BadCredentialsException.class,
        () -> service.login(request, servletRequest),
        "BadCredentialsException must propagate even if audit event publication fails");

    verify(loginAttempts).recordFailure("user1");
  }
}
