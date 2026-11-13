package com.plantshop.auth.github;

import com.github.benmanes.caffeine.cache.Ticker;
import com.plantshop.auth.config.AuthMode;
import com.plantshop.auth.config.ConditionalOnAuthMode;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** GitHub-mode beans: client, token introspector, token exchange and its rate limiter. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnAuthMode(AuthMode.GITHUB)
public class GitHubAuthConfig {

  /**
   * GitHub HTTP client.
   *
   * @param properties GitHub settings
   * @return the client
   */
  @Bean
  public GitHubClient gitHubClient(GitHubProperties properties) {
    return new GitHubClient(properties);
  }

  /**
   * Cache time source; tests replace it with a fake ticker.
   *
   * @return the system ticker
   */
  @Bean
  public Ticker gitHubTokenCacheTicker() {
    return Ticker.systemTicker();
  }

  /**
   * GitHub token introspector used by the resource server.
   *
   * @param client GitHub client
   * @param properties GitHub settings
   * @param ticker cache time source
   * @param meters metrics
   * @return the introspector
   */
  @Bean
  public GitHubTokenIntrospector gitHubTokenIntrospector(
      GitHubClient client, GitHubProperties properties, Ticker ticker, MeterRegistry meters) {
    return new GitHubTokenIntrospector(client, properties, ticker, meters);
  }

  /**
   * Token-exchange proxy logic.
   *
   * @param client GitHub client
   * @param properties GitHub settings
   * @param meters metrics
   * @return the service
   */
  @Bean
  public TokenExchangeService tokenExchangeService(
      GitHubClient client, GitHubProperties properties, MeterRegistry meters) {
    return new TokenExchangeService(client, properties, meters);
  }

  /**
   * Rate limiter for the token-exchange endpoint (applied by the API security chain).
   *
   * @param capacity requests per period
   * @param refillPeriod refill period
   * @return the limiter
   */
  @Bean
  public TokenExchangeRateLimiter tokenExchangeRateLimiter(
      @Value("${app.rate-limit.token-exchange.capacity:10}") long capacity,
      @Value("${app.rate-limit.token-exchange.refill-period:1m}") Duration refillPeriod) {
    return new TokenExchangeRateLimiter(capacity, refillPeriod);
  }
}
