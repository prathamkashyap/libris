package com.example.lms.service;

import com.example.lms.dto.*;
import com.example.lms.entity.*;
import com.example.lms.event.EntityAuditEvent;
import com.example.lms.repository.AccountRepository;
import com.example.lms.util.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.context.*;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
  private final AuthenticationManager authenticationManager;
  private final AccountRepository accounts;
  private final ApplicationEventPublisher events;
  private final CurrentUser currentUser;

  public AuthService(
      AuthenticationManager authenticationManager,
      AccountRepository accounts,
      ApplicationEventPublisher events,
      CurrentUser currentUser) {
    this.authenticationManager = authenticationManager;
    this.accounts = accounts;
    this.events = events;
    this.currentUser = currentUser;
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
}
