package com.example.lms.dto;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
    Instant timestamp,
    int status,
    String code,
    String message,
    String path,
    List<FieldError> fieldErrors) {
  public record FieldError(String field, String message) {}
}
