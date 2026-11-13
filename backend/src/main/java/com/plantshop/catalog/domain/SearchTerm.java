package com.plantshop.catalog.domain;

import com.plantshop.common.error.ApiException;
import com.plantshop.common.error.ProblemCode;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A normalised product-name search term (BR-04). Normalisation collapses every run of Unicode
 * {@code White_Space} characters to one space and trims leading/trailing spaces; the result must be
 * 2–50 characters (Unicode code points). The SPA applies the same algorithm, verified by shared
 * test vectors in {@code docs/api/test-vectors/search-normalisation.json}.
 *
 * @param value the normalised term
 */
public record SearchTerm(String value) {

  /** Minimum length after normalisation. */
  public static final int MIN_LENGTH = 2;

  /** Maximum length after normalisation. */
  public static final int MAX_LENGTH = 50;

  private static final Pattern WHITESPACE = Pattern.compile("\\p{IsWhite_Space}+");

  /**
   * Validates an already normalised value.
   *
   * @param value the normalised term
   * @throws ApiException {@code invalid-search-term} when the length is out of range
   */
  public SearchTerm {
    int length = value.codePointCount(0, value.length());
    if (length < MIN_LENGTH || length > MAX_LENGTH) {
      throw new ApiException(
          ProblemCode.INVALID_SEARCH_TERM,
          "Search term must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters long.",
          Map.of("minLength", MIN_LENGTH, "maxLength", MAX_LENGTH));
    }
  }

  /**
   * Normalises raw user input: collapse Unicode whitespace runs to one space, then trim.
   *
   * @param raw the raw input (may be {@code null})
   * @return the normalised string; empty for {@code null} or blank input
   */
  public static String normalize(String raw) {
    if (raw == null) {
      return "";
    }
    String collapsed = WHITESPACE.matcher(raw).replaceAll(" ");
    int start = collapsed.startsWith(" ") ? 1 : 0;
    int end = collapsed.endsWith(" ") ? collapsed.length() - 1 : collapsed.length();
    return start >= end ? "" : collapsed.substring(start, end);
  }

  /**
   * Parses an optional query parameter. Absent or blank input means "no search".
   *
   * @param raw the raw query parameter (may be {@code null})
   * @return the term, or empty when no search was requested
   * @throws ApiException {@code invalid-search-term} when the normalised length is out of range
   */
  public static Optional<SearchTerm> parse(String raw) {
    String normalized = normalize(raw);
    return normalized.isEmpty() ? Optional.empty() : Optional.of(new SearchTerm(normalized));
  }
}
