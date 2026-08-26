package com.example.lms.controller;

import com.example.lms.dto.AuthenticatedUserResponse;
import com.example.lms.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
@Tag(name = "Profile", description = "Current user profile endpoint")
public class ProfileController {
  private final AuthService authService;

  public ProfileController(AuthService authService) {
    this.authService = authService;
  }

  @GetMapping
  @Operation(summary = "Get Profile", description = "Get current authenticated user profile.")
  public AuthenticatedUserResponse get(Authentication authentication) {
    return authService.current(authentication.getName());
  }
}
