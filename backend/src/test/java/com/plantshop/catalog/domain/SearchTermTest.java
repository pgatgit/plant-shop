package com.plantshop.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.plantshop.common.error.ApiException;
import com.plantshop.common.error.ProblemCode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

@Tag("US-05")
class SearchTermTest {

  record Vector(String description, String input, String normalized, String outcome) {
    @Override
    public String toString() {
      return description;
    }
  }

  record Vectors(List<Vector> vectors) {}

  static Stream<Vector> sharedVectors() throws Exception {
    Path file = Path.of("..", "docs", "api", "test-vectors", "search-normalisation.json");
    Vectors vectors = JsonMapper.builder().build().readValue(Files.readString(file), Vectors.class);
    return vectors.vectors().stream();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("sharedVectors")
  @DisplayName("[BR-04] normalisation matches the shared frontend/backend vectors")
  void parse_matchesSharedVectors(Vector vector) {
    assertThat(SearchTerm.normalize(vector.input())).isEqualTo(vector.normalized());
    switch (vector.outcome()) {
      case "search" ->
          assertThat(SearchTerm.parse(vector.input()))
              .hasValueSatisfying(t -> assertThat(t.value()).isEqualTo(vector.normalized()));
      case "none" -> assertThat(SearchTerm.parse(vector.input())).isEmpty();
      case "invalid" ->
          assertThatThrownBy(() -> SearchTerm.parse(vector.input()))
              .isInstanceOfSatisfying(
                  ApiException.class,
                  e -> assertThat(e.code()).isEqualTo(ProblemCode.INVALID_SEARCH_TERM));
      default -> throw new IllegalArgumentException("Unknown outcome " + vector.outcome());
    }
  }

  @Test
  void normalize_null_returnsEmpty() {
    assertThat(SearchTerm.normalize(null)).isEmpty();
    assertThat(SearchTerm.parse(null)).isEmpty();
  }

  @Test
  void invalidTerm_exposesLengthBoundsAsProblemProperties() {
    assertThatThrownBy(() -> new SearchTerm("x"))
        .isInstanceOfSatisfying(
            ApiException.class,
            e ->
                assertThat(e.properties())
                    .containsEntry("minLength", 2)
                    .containsEntry("maxLength", 50));
  }
}
