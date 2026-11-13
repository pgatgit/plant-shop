package com.plantshop.auth.security;

import com.plantshop.common.error.ProblemCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Answers failed API authentication with Problem Details: 401 with an RFC 6750 Bearer challenge, or
 * 503 {@code auth-provider-unavailable} when the identity provider could not be reached.
 */
@Component
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

  /** Seconds a client should wait before retrying when the identity provider is down. */
  static final int PROVIDER_RETRY_AFTER_SECONDS = 30;

  private final ProblemResponseWriter writer;

  /**
   * Creates the entry point.
   *
   * @param writer problem body writer
   */
  public ProblemAuthenticationEntryPoint(ProblemResponseWriter writer) {
    this.writer = writer;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    if (authException instanceof AuthProviderUnavailableException) {
      response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(PROVIDER_RETRY_AFTER_SECONDS));
      writer.write(
          request,
          response,
          ProblemCode.AUTH_PROVIDER_UNAVAILABLE,
          "The sign-in service is temporarily unavailable. Please try again shortly.");
      return;
    }
    if (authException instanceof OAuth2AuthenticationException) {
      response.setHeader(
          HttpHeaders.WWW_AUTHENTICATE,
          "Bearer error=\"invalid_token\", error_description=\"The access token is invalid or"
              + " has expired\"");
      writer.write(
          request,
          response,
          ProblemCode.UNAUTHENTICATED,
          "The access token is invalid or has expired. Please sign in again.");
      return;
    }
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    writer.write(
        request,
        response,
        ProblemCode.UNAUTHENTICATED,
        "A valid access token is required. Please sign in.");
  }
}
