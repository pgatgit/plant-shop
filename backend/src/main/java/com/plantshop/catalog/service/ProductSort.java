package com.plantshop.catalog.service;

import java.util.Arrays;
import java.util.Optional;
import org.springframework.data.domain.Sort;

/**
 * The whitelisted catalog orderings (BR-03). Every ordering ends with deterministic tie-breakers
 * (name, then id) so pages never overlap or skip items (AC-04.3).
 */
public enum ProductSort {
  NAME_ASC("name,asc", Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"))),
  NAME_DESC("name,desc", Sort.by(Sort.Order.desc("name"), Sort.Order.asc("id"))),
  PRICE_ASC(
      "price,asc", Sort.by(Sort.Order.asc("price"), Sort.Order.asc("name"), Sort.Order.asc("id"))),
  PRICE_DESC(
      "price,desc",
      Sort.by(Sort.Order.desc("price"), Sort.Order.asc("name"), Sort.Order.asc("id")));

  /** The default ordering when no {@code sort} parameter is given. */
  public static final ProductSort DEFAULT = NAME_ASC;

  private final String parameter;
  private final Sort sort;

  ProductSort(String parameter, Sort sort) {
    this.parameter = parameter;
    this.sort = sort;
  }

  /**
   * Returns the API parameter value, e.g. {@code price,desc}.
   *
   * @return the parameter value
   */
  public String parameter() {
    return parameter;
  }

  /**
   * Returns the Spring Data sort including tie-breakers.
   *
   * @return the sort
   */
  public Sort toSort() {
    return sort;
  }

  /**
   * Parses an API parameter value (exact, case-sensitive match).
   *
   * @param parameter the {@code sort} query parameter
   * @return the ordering, or empty if the value is not whitelisted
   */
  public static Optional<ProductSort> fromParameter(String parameter) {
    return Arrays.stream(values()).filter(s -> s.parameter.equals(parameter)).findFirst();
  }
}
