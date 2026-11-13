package com.plantshop.auth.github;

/** GitHub could not be reached or answered with an unexpected status (timeouts, 5xx, 403). */
public class GitHubUnavailableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param message what failed (must not contain tokens or secrets)
   * @param cause the underlying failure, may be {@code null}
   */
  public GitHubUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
