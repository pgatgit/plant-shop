package com.plantshop.auth.github;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

/**
 * Per-client token buckets for the token-exchange endpoint, the only public endpoint that uses the
 * GitHub client secret (NFR-SEC-09, threat T8). In-memory: valid for a single instance only
 * (NFR-REL-07).
 */
public class TokenExchangeRateLimiter {

  private final long capacity;
  private final Duration refillPeriod;
  private final Cache<String, Bucket> buckets =
      Caffeine.newBuilder().expireAfterAccess(Duration.ofMinutes(10)).maximumSize(100_000).build();

  /**
   * Creates the limiter.
   *
   * @param capacity requests allowed per refill period
   * @param refillPeriod period after which the full capacity is restored
   */
  public TokenExchangeRateLimiter(long capacity, Duration refillPeriod) {
    this.capacity = capacity;
    this.refillPeriod = refillPeriod;
  }

  /**
   * Takes one token for the client.
   *
   * @param clientKey the client identifier (remote address)
   * @return empty when allowed; otherwise the seconds to wait before retrying
   */
  public OptionalLong tryAcquire(String clientKey) {
    Bucket bucket = buckets.get(clientKey, key -> newBucket());
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    if (probe.isConsumed()) {
      return OptionalLong.empty();
    }
    return OptionalLong.of(
        Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1));
  }

  private Bucket newBucket() {
    return Bucket.builder()
        .addLimit(
            Bandwidth.builder().capacity(capacity).refillIntervally(capacity, refillPeriod).build())
        .build();
  }
}
