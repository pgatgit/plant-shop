package com.plantshop.common.error;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates every exception raised by controllers into RFC 9457 Problem Details with a stable
 * {@code code} (docs/plan/04-backend-design.md §5). Stack traces are logged, never returned.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /**
   * Handles exceptions that carry their own problem code.
   *
   * @param ex the exception
   * @param request the current request
   * @return the problem response
   */
  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ProblemDetail> handleApiException(
      ApiException ex, HttpServletRequest request) {
    LOG.debug("Request rejected with {}: {}", ex.code().slug(), ex.getMessage());
    ProblemDetail problem =
        ProblemFactory.create(ex.code(), ex.getMessage(), request.getRequestURI(), ex.properties());
    return ResponseEntity.status(ex.code().status()).body(problem);
  }

  /**
   * Last-resort handler: logs the failure and returns a generic 500 problem.
   *
   * @param ex the unexpected exception
   * @param request the current request
   * @return a generic {@code internal-error} problem
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
    LOG.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), ex);
    ProblemDetail problem =
        ProblemFactory.create(
            ProblemCode.INTERNAL_ERROR,
            "An unexpected error occurred. Please try again later.",
            request.getRequestURI(),
            Map.of());
    return ResponseEntity.status(ProblemCode.INTERNAL_ERROR.status()).body(problem);
  }

  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<FieldError> errors =
        ex.getParameterValidationResults().stream().flatMap(this::fieldErrors).toList();
    return validationFailed(errors, headers, request);
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<FieldError> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                error ->
                    new FieldError(
                        error.getField(), error.getDefaultMessage(), error.getRejectedValue()))
            .toList();
    return validationFailed(errors, headers, request);
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    ProblemDetail problem =
        body instanceof ProblemDetail existing
            ? existing
            : ProblemDetail.forStatusAndDetail(status, "The request could not be processed.");
    Map<String, Object> properties = problem.getProperties();
    if (properties == null || !properties.containsKey("code")) {
      ProblemFactory.decorate(problem, ProblemCode.forStatus(status.value()), path(request));
    }
    if (status.is5xxServerError()) {
      LOG.error("Request failed with status {}", status.value(), ex);
    } else {
      LOG.debug("Request rejected with status {}: {}", status.value(), ex.getMessage());
    }
    return super.handleExceptionInternal(ex, problem, headers, status, request);
  }

  /**
   * Field errors of one parameter. A {@code @Valid} body is reported per bean property (e.g. {@code
   * quantity}), a simple parameter under its own name (e.g. {@code size}).
   */
  private Stream<FieldError> fieldErrors(ParameterValidationResult result) {
    if (result instanceof ParameterErrors beanErrors) {
      return beanErrors.getFieldErrors().stream()
          .map(
              error ->
                  new FieldError(
                      error.getField(), error.getDefaultMessage(), error.getRejectedValue()));
    }
    return result.getResolvableErrors().stream()
        .map(
            error ->
                new FieldError(
                    result.getMethodParameter().getParameterName(),
                    error.getDefaultMessage(),
                    result.getArgument()));
  }

  private ResponseEntity<Object> validationFailed(
      List<FieldError> errors, HttpHeaders headers, WebRequest request) {
    ProblemDetail problem =
        ProblemFactory.create(
            ProblemCode.VALIDATION_FAILED,
            "One or more request values are invalid.",
            path(request),
            Map.of("errors", errors));
    return ResponseEntity.status(ProblemCode.VALIDATION_FAILED.status())
        .headers(headers)
        .body(problem);
  }

  private static String path(WebRequest request) {
    return request instanceof ServletWebRequest servlet
        ? servlet.getRequest().getRequestURI()
        : null;
  }
}
