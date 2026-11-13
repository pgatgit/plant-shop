package com.plantshop.common.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Page-number pagination envelope (ADR-015). {@code page} is 0-based.
 *
 * @param content the items on this page
 * @param page 0-based page index
 * @param size requested page size
 * @param totalElements total number of matching items
 * @param totalPages total number of pages
 * @param sort the applied sort, e.g. {@code name,asc}
 * @param query the normalised search term, or {@code null} when not searching
 * @param <T> the item type
 */
public record PageResponse<T>(
    List<T> content,
    @Schema(example = "0") int page,
    @Schema(example = "12") int size,
    @Schema(example = "28") long totalElements,
    @Schema(example = "3") int totalPages,
    @Schema(example = "name,asc") String sort,
    @Schema(nullable = true, example = "mon") String query) {

  /**
   * Creates a response from a Spring Data page, mapping each element.
   *
   * @param page the page of source elements
   * @param mapper element mapper
   * @param sort the applied sort expression
   * @param query the normalised search term, or {@code null}
   * @param <S> the source element type
   * @param <T> the response element type
   * @return the envelope
   */
  public static <S, T> PageResponse<T> of(
      Page<S> page, Function<S, T> mapper, String sort, String query) {
    return new PageResponse<>(
        page.getContent().stream().map(mapper).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        sort,
        query);
  }
}
