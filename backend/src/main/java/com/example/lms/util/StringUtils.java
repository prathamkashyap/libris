package com.example.lms.util;

public final class StringUtils {
  private StringUtils() {}

  public static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
