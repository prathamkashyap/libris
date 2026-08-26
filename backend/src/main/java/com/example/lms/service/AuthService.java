package com.example.lms.service;

import com.example.lms.dto.*;
import com.example.lms.entity.*;
import com.example.lms.event.EntityAuditEvent;
import com.example.lms.exception.ConflictException;
import com.example.lms.repository.AccountRepository;
import com.example.lms.repository.StudentProfileRepository;
import com.example.lms.util.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.context.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private final AuthenticationManager authenticationManager;
  private final AccountRepository accounts;
  private final StudentProfileRepository studentProfiles;
  private final ApplicationEventPublisher events;
  private final CurrentUser currentUser;
  private final PasswordEncoder passwordEncoder;

  public AuthService(
      AuthenticationManager authenticationManager,
      AccountRepository accounts,
      StudentProfileRepository studentProfiles,
      ApplicationEventPublisher events,
      CurrentUser currentUser,
      PasswordEncoder passwordEncoder) {
    this.authenticationManager = authenticationManager;
    this.accounts = accounts;
    this.studentProfiles = studentProfiles;
    this.events = events;
    this.currentUser = currentUser;
    this.passwordEncoder = passwordEncoder;
  }

  public AuthenticatedUserResponse login(LoginRequest request, HttpServletRequest servletRequest) {
    Authentication authentication =
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(
                request.username(), request.password()));
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    servletRequest.getSession(true).setAttribute("SPRING_SECURITY_CONTEXT", context);

    var account = accounts.findByUsername(authentication.getName()).orElseThrow();
    var actor = currentUser.get();
    events.publishEvent(
        new EntityAuditEvent(
            this,
            AuditAction.LOGIN,
            AuditEntityType.ACCOUNT,
            account.getId(),
            "User logged in: " + account.getUsername(),
            account.getId(),
            account.getUsername(),
            account.getRole().name(),
            actor.ipAddress(),
            actor.userAgent()));

    return new AuthenticatedUserResponse(
        account.getId(), account.getUsername(), account.getRole().name(), account.getUsername());
  }

  public AuthenticatedUserResponse current(String username) {
    var account = accounts.findByUsername(username).orElseThrow();
    return new AuthenticatedUserResponse(
        account.getId(), account.getUsername(), account.getRole().name(), account.getUsername());
  }

  public void logout(HttpServletRequest request) {
    var actor = currentUser.get();
    var account = accounts.findByUsername(actor.username()).orElse(null);
    if (account != null) {
      events.publishEvent(
          new EntityAuditEvent(
              this,
              AuditAction.LOGOUT,
              AuditEntityType.ACCOUNT,
              account.getId(),
              "User logged out: " + account.getUsername(),
              account.getId(),
              account.getUsername(),
              account.getRole().name(),
              actor.ipAddress(),
              actor.userAgent()));
    }
    var session = request.getSession(false);
    if (session != null) session.invalidate();
    SecurityContextHolder.clearContext();
  }

  @Transactional
  public void registerStudent(RegisterRequest request) {
    if (accounts.findByUsername(request.username()).isPresent())
      throw new ConflictException("Username is already in use.");
    if (studentProfiles.findByEmail(request.email()).isPresent())
      throw new ConflictException("Email is already registered.");

    StudentProfile profile;
    try {
      Account account = new Account();
      account.setUsername(request.username());
      account.setPasswordHash(passwordEncoder.encode(request.password()));
      account.setRole(Role.STUDENT);
      account = accounts.save(account);

      profile = new StudentProfile();
      profile.setAccount(account);
      profile.setName(request.name());
      profile.setEmail(request.email());
      profile.setPhone(request.phone());
      profile = studentProfiles.save(profile);
    } catch (DataIntegrityViolationException e) {
      throw new ConflictException("Username or email is already in use.");
    }

    events.publishEvent(
        new EntityAuditEvent(
            this,
            AuditAction.CREATE,
            AuditEntityType.STUDENT,
            profile.getId(),
            "Student self-registered: " + request.name(),
            null, // No actor yet as they are unauthenticated
            "system",
            "SYSTEM",
            "unknown",
            "unknown"));
  }
}
