package com.plantshop.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class CorrelationTest {

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void currentTraceId_readsTheTracingMdcEntry() {
    assertThat(Correlation.currentTraceId()).isEmpty();

    MDC.put("traceId", "6f1c2b9a0d4e8f7a6f1c2b9a0d4e8f7a");

    assertThat(Correlation.currentTraceId()).contains("6f1c2b9a0d4e8f7a6f1c2b9a0d4e8f7a");
  }

  @Test
  void ownerHash_isStableEightCharSha256Prefix() {
    // sha256("local:shopper") starts with these 8 hex characters.
    String hash = Correlation.ownerHash("local:shopper");

    assertThat(hash).hasSize(8).matches("[0-9a-f]{8}");
    assertThat(Correlation.ownerHash("local:shopper")).isEqualTo(hash);
    assertThat(Correlation.ownerHash("github:1")).isNotEqualTo(hash);
  }
}
