package com.plantshop.auth.security;

import org.springframework.security.core.AuthenticationException;

/**
 * The identity provider could not validate a token (e.g. GitHub timed out). Mapped to 503 {@code
 * auth-provider-unavailable} with {@code Retry-After} (NFR-REL-05) instead of 401, so clients retry
 * rather than discard a possibly valid token.
 */
public class AuthProviderUnavailableException extends AuthenticationException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param message description (no secrets)
   * @param cause the underlying failure
   */
  public AuthProviderUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
