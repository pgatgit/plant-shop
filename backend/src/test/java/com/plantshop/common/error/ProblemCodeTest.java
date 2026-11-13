package com.plantshop.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProblemCodeTest {

  @ParameterizedTest
  @CsvSource({
    "400,MALFORMED_REQUEST",
    "401,UNAUTHENTICATED",
    "403,FORBIDDEN",
    "404,NOT_FOUND",
    "405,METHOD_NOT_ALLOWED",
    "406,NOT_ACCEPTABLE",
    "415,UNSUPPORTED_MEDIA_TYPE",
    "500,INTERNAL_ERROR",
    "418,INTERNAL_ERROR"
  })
  void forStatus_mapsFrameworkStatusesToGenericCodes(int status, ProblemCode expected) {
    assertThat(ProblemCode.forStatus(status)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({"VALIDATION_FAILED,validation-failed", "INVALID_SEARCH_TERM,invalid-search-term"})
  void type_isDerivedFromSlug(ProblemCode code, String slug) {
    assertThat(code.slug()).isEqualTo(slug);
    assertThat(code.type()).hasToString("https://plantshop.example/problems/" + slug);
  }
}
