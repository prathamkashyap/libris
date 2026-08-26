package com.example.lms.dto;

import jakarta.validation.constraints.*;

public record LibrarianUpdateRequest(
    @NotBlank @Size(max = 50) String username,
    @NotBlank @Size(max = 100) String name,
    @Min(18) @Max(100) int age,
    @NotBlank @Size(max = 20) String phone) {}
