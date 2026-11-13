package com.plantshop.auth.security;

import com.plantshop.common.error.ProblemCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Answers requests lacking the required scope with 403 Problem Details. */
@Component
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

  private final ProblemResponseWriter writer;

  /**
   * Creates the handler.
   *
   * @param writer problem body writer
   */
  public ProblemAccessDeniedHandler(ProblemResponseWriter writer) {
    this.writer = writer;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    writer.write(
        request,
        response,
        ProblemCode.FORBIDDEN,
        "Your account is not allowed to perform this action.");
  }
}
