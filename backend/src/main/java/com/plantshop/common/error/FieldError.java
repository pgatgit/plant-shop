package com.plantshop.common.error;

/**
 * One entry of the {@code errors} array in a {@code validation-failed} problem.
 *
 * @param field the request field or parameter name
 * @param message user-safe description of the violation
 * @param rejectedValue the value that was rejected (may be {@code null})
 */
public record FieldError(String field, String message, Object rejectedValue) {}
