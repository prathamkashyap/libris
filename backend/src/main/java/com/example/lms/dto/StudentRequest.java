package com.example.lms.dto;
import jakarta.validation.constraints.*;
public record StudentRequest(@NotBlank @Size(max=50) String username,@NotBlank @Size(min=8,max=100) String password,@NotBlank @Size(max=100) String name,@NotBlank @Email(message="Invalid email address.") @Size(max=100) String email,@NotBlank @Size(max=20) String phone){}
