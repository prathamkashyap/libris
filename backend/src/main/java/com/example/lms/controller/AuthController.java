package com.example.lms.controller;
import com.example.lms.dto.*;
import com.example.lms.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth") @Tag(name="Authentication",description="Session-based login, logout, and CSRF bootstrap")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service = service; }
    @GetMapping("/csrf") @Operation(summary="Get CSRF token",description="Required before POST requests. Returns a token that must be sent as X-XSRF-TOKEN header.") public CsrfToken csrf(CsrfToken token) { return token; }
    @PostMapping("/login") @Operation(summary="Authenticate",description="Login with username and password. Creates a session.") @ApiResponse(responseCode="200",description="Authenticated successfully") public AuthenticatedUserResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) { return service.login(request, servletRequest); }
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(summary="Logout",description="Invalidate the current session.") @ApiResponse(responseCode="204",description="Logged out") public void logout(HttpServletRequest request) { service.logout(request); }
    @GetMapping("/me") @Operation(summary="Current user",description="Returns the authenticated user's profile.") public AuthenticatedUserResponse me(Authentication authentication) { return service.current(authentication.getName()); }
}
