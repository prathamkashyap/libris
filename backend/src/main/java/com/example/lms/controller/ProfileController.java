package com.example.lms.controller;

import com.example.lms.dto.AuthenticatedUserResponse;
import com.example.lms.repository.AccountRepository;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
  private final AccountRepository accounts;

  public ProfileController(AccountRepository a) {
    accounts = a;
  }

  @GetMapping
  public AuthenticatedUserResponse get(Authentication authentication) {
    var a = accounts.findByUsername(authentication.getName()).orElseThrow();
    return new AuthenticatedUserResponse(
        a.getId(), a.getUsername(), a.getRole().name(), a.getUsername());
  }
}
