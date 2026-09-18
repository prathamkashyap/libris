package com.example.lms.security;

import jakarta.servlet.http.*;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {
  private static final Logger log = LoggerFactory.getLogger(RestAccessDeniedHandler.class);

  private final RestAuthenticationEntryPoint response;

  public RestAccessDeniedHandler(RestAuthenticationEntryPoint response) {
    this.response = response;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse servletResponse,
      AccessDeniedException exception)
      throws IOException {
    log.warn("Access denied: uri={}", request.getRequestURI());
    response.write(
        request,
        servletResponse,
        HttpStatus.FORBIDDEN,
        "FORBIDDEN",
        "You do not have permission to perform this action.");
  }
}
