package com.plantshop.common.error;

import com.plantshop.common.web.Correlation;
import java.net.URI;
import java.util.Map;
import org.springframework.http.ProblemDetail;

/** Builds RFC 9457 Problem Details bodies with the project's {@code type}/{@code code} scheme. */
public final class ProblemFactory {

  private ProblemFactory() {}

  /**
   * Creates a problem for the given code.
   *
   * @param code the problem code
   * @param detail user-safe explanation of this occurrence
   * @param instance the request path, or {@code null}
   * @param properties extension members
   * @return a fully populated problem
   */
  public static ProblemDetail create(
      ProblemCode code, String detail, String instance, Map<String, Object> properties) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
    decorate(problem, code, instance);
    properties.forEach(problem::setProperty);
    return problem;
  }

  /**
   * Adds {@code type}, {@code title}, {@code code}, {@code instance} and, when tracing is active,
   * {@code traceId} to an existing problem.
   *
   * @param problem the problem to decorate in place
   * @param code the problem code
   * @param instance the request path, or {@code null}
   */
  public static void decorate(ProblemDetail problem, ProblemCode code, String instance) {
    problem.setType(code.type());
    problem.setTitle(code.title());
    problem.setProperty("code", code.slug());
    if (instance != null) {
      problem.setInstance(URI.create(instance));
    }
    Correlation.currentTraceId().ifPresent(traceId -> problem.setProperty("traceId", traceId));
  }
}
