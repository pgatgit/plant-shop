package com.plantshop.auth.github;

/** An RFC 6749 §5.2 error returned by the token-exchange endpoint (HTTP 400). */
public class OAuthErrorException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String error;

  /**
   * Creates the exception.
   *
   * @param error RFC 6749 error code, e.g. {@code invalid_grant}
   * @param description human-readable description (no secrets)
   */
  public OAuthErrorException(String error, String description) {
    super(description);
    this.error = error;
  }

  /**
   * Creates an {@code invalid_request} error.
   *
   * @param description what is wrong
   * @return the exception
   */
  public static OAuthErrorException invalidRequest(String description) {
    return new OAuthErrorException("invalid_request", description);
  }

  /**
   * Returns the RFC 6749 error code.
   *
   * @return the error code
   */
  public String error() {
    return error;
  }
}
