package com.example.lms.service;

import com.example.lms.dto.*;
import com.example.lms.repository.AccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.context.*;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final AuthenticationManager authenticationManager; private final AccountRepository accounts;
    public AuthService(AuthenticationManager authenticationManager, AccountRepository accounts) { this.authenticationManager = authenticationManager; this.accounts = accounts; }
    public AuthenticatedUserResponse login(LoginRequest request, HttpServletRequest servletRequest) {
        Authentication authentication = authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        SecurityContext context = SecurityContextHolder.createEmptyContext(); context.setAuthentication(authentication); SecurityContextHolder.setContext(context);
        servletRequest.getSession(true).setAttribute("SPRING_SECURITY_CONTEXT", context); return current(authentication.getName());
    }
    public AuthenticatedUserResponse current(String username) { var account = accounts.findByUsername(username).orElseThrow(); return new AuthenticatedUserResponse(account.getId(), account.getUsername(), account.getRole().name(), account.getUsername()); }
    public void logout(HttpServletRequest request) { var session = request.getSession(false); if (session != null) session.invalidate(); SecurityContextHolder.clearContext(); }
}
