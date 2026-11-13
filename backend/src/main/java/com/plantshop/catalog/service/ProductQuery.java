package com.plantshop.catalog.service;

import com.plantshop.catalog.domain.SearchTerm;
import java.util.Objects;
import java.util.Optional;

/**
 * A validated catalog listing request.
 *
 * @param page 0-based page index
 * @param size page size
 * @param sort the ordering
 * @param searchTerm the name prefix to search for, or {@code null} to list everything
 */
public record ProductQuery(int page, int size, ProductSort sort, SearchTerm searchTerm) {

  /**
   * Validates required components.
   *
   * @param page 0-based page index
   * @param size page size
   * @param sort the ordering
   * @param searchTerm the search term or {@code null}
   */
  public ProductQuery {
    Objects.requireNonNull(sort, "sort");
  }

  /**
   * Returns the search term if this is a search.
   *
   * @return the search term, if any
   */
  public Optional<SearchTerm> search() {
    return Optional.ofNullable(searchTerm);
  }
}
