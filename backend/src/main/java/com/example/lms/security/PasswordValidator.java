package com.example.lms.security;

public final class PasswordValidator {

  private PasswordValidator() {}

  public static String validate(String password) {
    if (password == null || password.length() < 8) {
      return "Password must be at least 8 characters long.";
    }
    if (!password.matches(".*[A-Z].*")) {
      return "Password must contain at least one uppercase letter.";
    }
    if (!password.matches(".*[a-z].*")) {
      return "Password must contain at least one lowercase letter.";
    }
    if (!password.matches(".*\\d.*")) {
      return "Password must contain at least one digit.";
    }
    if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) {
      return "Password must contain at least one special character.";
    }
    return null;
  }
}
