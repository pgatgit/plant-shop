package com.plantshop.auth.security;

import com.plantshop.common.error.ProblemCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** Answers unauthenticated API requests with 401 Problem Details and a Bearer challenge. */
@Component
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

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
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    writer.write(
        request,
        response,
        ProblemCode.UNAUTHENTICATED,
        "A valid access token is required. Please sign in.");
  }
}
