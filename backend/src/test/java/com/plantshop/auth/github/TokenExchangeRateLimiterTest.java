package com.plantshop.auth.github;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenExchangeRateLimiterTest {

  @Test
  @DisplayName("[NFR-SEC-09] each client gets `capacity` requests, then must wait")
  void limitsPerClient() {
    TokenExchangeRateLimiter limiter = new TokenExchangeRateLimiter(3, Duration.ofMinutes(1));

    assertThat(limiter.tryAcquire("10.0.0.1")).isEmpty();
    assertThat(limiter.tryAcquire("10.0.0.1")).isEmpty();
    assertThat(limiter.tryAcquire("10.0.0.1")).isEmpty();
    java.util.OptionalLong wait = limiter.tryAcquire("10.0.0.1");
    assertThat(wait).isPresent();
    assertThat(wait.getAsLong()).isBetween(1L, 61L);
    assertThat(limiter.tryAcquire("10.0.0.2")).as("other clients are unaffected").isEmpty();
  }
}
