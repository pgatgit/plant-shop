package com.plantshop.auth.security;

import com.plantshop.common.error.ProblemCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes Problem Details bodies from the security filter chain, which runs before Spring MVC and
 * therefore cannot use {@code GlobalExceptionHandler}.
 */
@Component
public class ProblemResponseWriter {

  private final JsonMapper jsonMapper;

  /**
   * Creates the writer.
   *
   * @param jsonMapper the application's JSON mapper
   */
  public ProblemResponseWriter(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  /**
   * Writes a problem response for the given code.
   *
   * @param request the current request
   * @param response the response to write to
   * @param code the problem code (its status is used)
   * @param detail user-safe detail message
   * @throws IOException if writing fails
   */
  public void write(
      HttpServletRequest request, HttpServletResponse response, ProblemCode code, String detail)
      throws IOException {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", code.type().toString());
    body.put("title", code.title());
    body.put("status", code.status().value());
    body.put("detail", detail);
    body.put("instance", request.getRequestURI());
    body.put("code", code.slug());
    response.setStatus(code.status().value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    jsonMapper.writeValue(response.getOutputStream(), body);
  }
}
