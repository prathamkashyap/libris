package com.example.lms.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Accepts the raw CSRF token sent by this JavaScript client while retaining Spring Security's XOR
 * protection for conventional form submissions.
 */
public final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
  private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
  private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();
  private final RequestMatcher csrfHeader = new RequestHeaderRequestMatcher("X-XSRF-TOKEN");

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
    xor.handle(request, response, csrfToken);
    csrfToken.get();
  }

  @Override
  public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
    String plainToken = plain.resolveCsrfTokenValue(request, csrfToken);
    return csrfHeader.matches(request) ? plainToken : xor.resolveCsrfTokenValue(request, csrfToken);
  }
}
