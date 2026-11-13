package com.plantshop.auth.github;

import com.plantshop.auth.security.ProblemResponseWriter;
import com.plantshop.common.error.ProblemCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.OptionalLong;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Applies {@link TokenExchangeRateLimiter} to {@code POST /api/v1/auth/token}. Added to the API
 * security chain after the CORS filter, so 429 responses stay readable by the SPA. Deliberately not
 * a Spring bean (Boot would otherwise also register it as a servlet filter).
 */
public class TokenExchangeRateLimitFilter extends OncePerRequestFilter {

  /** The protected path. */
  public static final String PATH = "/api/v1/auth/token";

  private final TokenExchangeRateLimiter limiter;
  private final ProblemResponseWriter writer;

  /**
   * Creates the filter.
   *
   * @param limiter the per-client limiter
   * @param writer problem body writer
   */
  public TokenExchangeRateLimitFilter(
      TokenExchangeRateLimiter limiter, ProblemResponseWriter writer) {
    this.limiter = limiter;
    this.writer = writer;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !("POST".equals(request.getMethod()) && PATH.equals(request.getRequestURI()));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    OptionalLong waitSeconds = limiter.tryAcquire(request.getRemoteAddr());
    if (waitSeconds.isEmpty()) {
      chain.doFilter(request, response);
      return;
    }
    response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(waitSeconds.getAsLong()));
    writer.write(
        request,
        response,
        ProblemCode.RATE_LIMITED,
        "Too many sign-in attempts. Please wait "
            + waitSeconds.getAsLong()
            + " seconds and try again.");
  }
}
