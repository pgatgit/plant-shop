package com.plantshop.auth.security;

import com.plantshop.common.web.Correlation;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds the pseudonymised owner ({@code ownerHash}) of an authenticated API request to the MDC so
 * every log line of that request can be correlated per user without logging the id (NFR-OBS-02).
 * Runs inside the API security chain, after bearer token authentication. Not a bean, so it is not
 * also registered as a servlet filter.
 */
public final class OwnerMdcFilter extends OncePerRequestFilter {

  private final CurrentUserResolver currentUsers;

  /**
   * Creates the filter.
   *
   * @param currentUsers maps the authentication to an owner id
   */
  public OwnerMdcFilter(CurrentUserResolver currentUsers) {
    this.currentUsers = currentUsers;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    boolean added = false;
    if (authentication != null && authentication.isAuthenticated()) {
      try {
        String ownerId = currentUsers.resolve(authentication).ownerId();
        MDC.put(Correlation.MDC_OWNER, Correlation.ownerHash(ownerId));
        added = true;
      } catch (InsufficientAuthenticationException ignored) {
        // Not one of our token types (e.g. anonymous): nothing to correlate.
      }
    }
    try {
      chain.doFilter(request, response);
    } finally {
      if (added) {
        MDC.remove(Correlation.MDC_OWNER);
      }
    }
  }
}
