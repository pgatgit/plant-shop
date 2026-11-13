package com.plantshop.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;

class ProblemFactoryTest {

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  @DisplayName("[NFR-OBS-02] problems carry the current traceId")
  void create_addsTraceIdWhenTracing() {
    MDC.put("traceId", "0123456789abcdef0123456789abcdef");

    ProblemDetail problem =
        ProblemFactory.create(ProblemCode.FORBIDDEN, "No.", "/api/v1/cart", Map.of("limit", 3));

    assertThat(problem.getProperties())
        .containsEntry("code", "forbidden")
        .containsEntry("limit", 3)
        .containsEntry("traceId", "0123456789abcdef0123456789abcdef");
    assertThat(problem.getInstance()).hasToString("/api/v1/cart");
  }

  @Test
  void create_withoutTracing_omitsTraceId() {
    ProblemDetail problem = ProblemFactory.create(ProblemCode.FORBIDDEN, "No.", null, Map.of());

    assertThat(problem.getProperties()).doesNotContainKey("traceId");
    assertThat(problem.getInstance()).isNull();
  }
}
