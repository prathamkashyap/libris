package com.example.lms.security;

import com.example.lms.dto.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
  private final ObjectMapper mapper;

  public RestAuthenticationEntryPoint(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    String accept = request.getHeader("Accept");
    boolean wantsHtml = accept != null && accept.contains("text/html");
    String requestUri = request.getRequestURI();

    if (wantsHtml && !requestUri.startsWith("/api/")) {
      // Browser navigation to HTML page - redirect to login
      String loginUrl = "/login.html?redirect=" + java.net.URLEncoder.encode(requestUri, "UTF-8");
      response.sendRedirect(loginUrl);
      return;
    }

    // API request - return JSON error
    write(
        request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required.");
  }

  void write(
      HttpServletRequest request,
      HttpServletResponse response,
      HttpStatus status,
      String code,
      String message)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    mapper.writeValue(
        response.getOutputStream(),
        new ApiErrorResponse(
            Instant.now(), status.value(), code, message, request.getRequestURI(), List.of()));
  }
}
