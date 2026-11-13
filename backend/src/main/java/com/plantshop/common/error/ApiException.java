package com.plantshop.common.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base class for exceptions that map directly to a Problem Details response. The {@code detail}
 * message must be safe to show to end users; the SPA displays it verbatim for 4xx responses.
 */
public class ApiException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final ProblemCode code;
  private final transient Map<String, Object> properties;

  /**
   * Creates an exception without extra problem properties.
   *
   * @param code the problem code (determines status, title and type)
   * @param detail user-safe explanation of this occurrence
   */
  public ApiException(ProblemCode code, String detail) {
    this(code, detail, Map.of());
  }

  /**
   * Creates an exception with extra problem properties (e.g. {@code limit}, {@code errors}).
   *
   * @param code the problem code (determines status, title and type)
   * @param detail user-safe explanation of this occurrence
   * @param properties extension members added to the problem body, in insertion order
   */
  public ApiException(ProblemCode code, String detail, Map<String, Object> properties) {
    super(detail);
    this.code = code;
    this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
  }

  /**
   * Returns the problem code.
   *
   * @return the code
   */
  public ProblemCode code() {
    return code;
  }

  /**
   * Returns the extension members to add to the problem body.
   *
   * @return an unmodifiable map
   */
  public Map<String, Object> properties() {
    return properties;
  }
}
