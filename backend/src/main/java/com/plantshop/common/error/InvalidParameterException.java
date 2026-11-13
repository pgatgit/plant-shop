package com.plantshop.common.error;

import java.util.List;
import java.util.Map;

/** Thrown when a request parameter is syntactically valid but not an accepted value. */
public class InvalidParameterException extends ApiException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates a {@code validation-failed} problem for a single parameter.
   *
   * @param parameter the parameter name
   * @param message user-safe description of the accepted values
   * @param rejectedValue the rejected value
   */
  public InvalidParameterException(String parameter, String message, Object rejectedValue) {
    super(
        ProblemCode.VALIDATION_FAILED,
        "Request parameter '" + parameter + "' is invalid.",
        Map.of("errors", List.of(new FieldError(parameter, message, rejectedValue))));
  }
}
