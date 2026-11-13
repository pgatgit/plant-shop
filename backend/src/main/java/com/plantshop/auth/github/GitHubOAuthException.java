package com.plantshop.auth.github;

/**
 * GitHub rejected an authorization-code exchange. GitHub reports this with HTTP 200 and an {@code
 * error} member (e.g. {@code bad_verification_code}).
 */
public class GitHubOAuthException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String error;

  /**
   * Creates the exception.
   *
   * @param error GitHub's error code
   */
  public GitHubOAuthException(String error) {
    super("GitHub rejected the authorization code: " + error);
    this.error = error;
  }

  /**
   * Returns GitHub's error code.
   *
   * @return the error code
   */
  public String error() {
    return error;
  }
}
