package com.plantshop.common.error;

import java.net.URI;
import org.springframework.http.HttpStatus;

/**
 * Stable, documented error codes returned in the {@code code} property of every Problem Details
 * response (docs/plan/04-backend-design.md §5). The {@code type} URI is derived from the code.
 */
public enum ProblemCode {
  VALIDATION_FAILED("validation-failed", "Validation failed", HttpStatus.BAD_REQUEST),
  MALFORMED_REQUEST("malformed-request", "Malformed request", HttpStatus.BAD_REQUEST),
  INVALID_SEARCH_TERM("invalid-search-term", "Invalid search term", HttpStatus.BAD_REQUEST),
  UNAUTHENTICATED("unauthenticated", "Authentication required", HttpStatus.UNAUTHORIZED),
  FORBIDDEN("forbidden", "Access denied", HttpStatus.FORBIDDEN),
  NOT_FOUND("not-found", "Resource not found", HttpStatus.NOT_FOUND),
  METHOD_NOT_ALLOWED("method-not-allowed", "Method not allowed", HttpStatus.METHOD_NOT_ALLOWED),
  NOT_ACCEPTABLE("not-acceptable", "Not acceptable", HttpStatus.NOT_ACCEPTABLE),
  UNSUPPORTED_MEDIA_TYPE(
      "unsupported-media-type", "Unsupported media type", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
  INTERNAL_ERROR("internal-error", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);

  /** Base of all problem {@code type} URIs; they are identifiers and need not resolve. */
  public static final String TYPE_BASE = "https://plantshop.example/problems/";

  private final String slug;
  private final String title;
  private final HttpStatus status;

  ProblemCode(String slug, String title, HttpStatus status) {
    this.slug = slug;
    this.title = title;
    this.status = status;
  }

  /**
   * Returns the machine-readable code, e.g. {@code validation-failed}.
   *
   * @return the code slug
   */
  public String slug() {
    return slug;
  }

  /**
   * Returns the short, human-readable summary used as the problem title.
   *
   * @return the title
   */
  public String title() {
    return title;
  }

  /**
   * Returns the HTTP status associated with this code.
   *
   * @return the status
   */
  public HttpStatus status() {
    return status;
  }

  /**
   * Returns the problem {@code type} URI for this code.
   *
   * @return the type URI
   */
  public URI type() {
    return URI.create(TYPE_BASE + slug);
  }

  /**
   * Picks the generic code for a status produced by the framework rather than by our code.
   *
   * @param statusCode the HTTP status code
   * @return the matching generic code; {@link #INTERNAL_ERROR} for 5xx and unknown statuses
   */
  public static ProblemCode forStatus(int statusCode) {
    return switch (statusCode) {
      case 400 -> MALFORMED_REQUEST;
      case 401 -> UNAUTHENTICATED;
      case 403 -> FORBIDDEN;
      case 404 -> NOT_FOUND;
      case 405 -> METHOD_NOT_ALLOWED;
      case 406 -> NOT_ACCEPTABLE;
      case 415 -> UNSUPPORTED_MEDIA_TYPE;
      default -> INTERNAL_ERROR;
    };
  }
}
