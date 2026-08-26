package com.example.lms.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest(
    @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        String username,
    @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        String password,
    @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,
    @NotBlank(message = "Email is required")
        @Email(message = "Invalid email address.")
        @Size(max = 100, message = "Email must be at most 100 characters")
        String email,
    @NotBlank(message = "Phone is required")
        @Size(max = 20, message = "Phone must be at most 20 characters")
        String phone) {}
