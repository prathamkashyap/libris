package com.example.lms.dto;
import jakarta.validation.constraints.*;
public record StudentUpdateRequest(@NotBlank @Size(max=50) String username,@NotBlank @Size(max=100) String name,@NotBlank @Email(message="Invalid email address.") @Size(max=100) String email,@NotBlank @Size(max=20) String phone){}
