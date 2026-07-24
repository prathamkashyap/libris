package com.example.lms.controller;
import com.example.lms.dto.*;
import com.example.lms.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service = service; }
    @GetMapping("/csrf") public CsrfToken csrf(CsrfToken token) { return token; }
    @PostMapping("/login") public AuthenticatedUserResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) { return service.login(request, servletRequest); }
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT) public void logout(HttpServletRequest request) { service.logout(request); }
    @GetMapping("/me") public AuthenticatedUserResponse me(Authentication authentication) { return service.current(authentication.getName()); }
}
