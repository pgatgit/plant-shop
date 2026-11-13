package com.plantshop.auth.github;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.plantshop.auth.security.AuthProviderUnavailableException;
import com.plantshop.auth.security.Scopes;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

/**
 * Validates GitHub access tokens (D3, ADR-002). Valid tokens are cached for {@code
 * app.github.token-cache-ttl} and invalid ones for {@code app.github.negative-cache-ttl}; cache
 * keys are SHA-256 hashes, so raw tokens are never held in memory beyond the request.
 */
public class GitHubTokenIntrospector implements OpaqueTokenIntrospector {

  /** Attribute holding the GitHub login. */
  public static final String ATTR_LOGIN = "login";

  /** Attribute holding the display name. */
  public static final String ATTR_NAME = "name";

  /** Attribute holding the avatar URL. */
  public static final String ATTR_AVATAR_URL = "avatar_url";

  private static final List<GrantedAuthority> AUTHORITIES =
      AuthorityUtils.createAuthorityList(
          "SCOPE_" + Scopes.CATALOG_READ,
          "SCOPE_" + Scopes.CART_READ,
          "SCOPE_" + Scopes.CART_WRITE,
          "ROLE_SHOPPER");

  private final GitHubClient client;
  private final Cache<String, OAuth2AuthenticatedPrincipal> valid;
  private final Cache<String, Boolean> invalid;
  private final MeterRegistry meters;

  /**
   * Creates the introspector.
   *
   * @param client GitHub client
   * @param properties cache TTLs
   * @param ticker time source for cache expiry (tests use a fake)
   * @param meters metrics registry
   */
  public GitHubTokenIntrospector(
      GitHubClient client, GitHubProperties properties, Ticker ticker, MeterRegistry meters) {
    this.client = client;
    this.meters = meters;
    this.valid =
        Caffeine.newBuilder()
            .ticker(ticker)
            .expireAfterWrite(properties.tokenCacheTtl())
            .maximumSize(10_000)
            .build();
    this.invalid =
        Caffeine.newBuilder()
            .ticker(ticker)
            .expireAfterWrite(properties.negativeCacheTtl())
            .maximumSize(10_000)
            .build();
  }

  @Override
  public OAuth2AuthenticatedPrincipal introspect(String token) {
    String key = sha256(token);
    OAuth2AuthenticatedPrincipal cached = valid.getIfPresent(key);
    if (cached != null) {
      count("cache", "valid");
      return cached;
    }
    if (invalid.getIfPresent(key) != null) {
      count("cache", "invalid");
      throw new BadOpaqueTokenException("Invalid token");
    }
    Optional<GitHubUser> user;
    try {
      user = client.checkToken(token);
    } catch (GitHubUnavailableException e) {
      count("github", "unavailable");
      throw new AuthProviderUnavailableException("GitHub is unavailable", e);
    }
    if (user.isEmpty()) {
      count("github", "invalid");
      invalid.put(key, Boolean.TRUE);
      throw new BadOpaqueTokenException("Invalid token");
    }
    count("github", "valid");
    OAuth2AuthenticatedPrincipal principal = toPrincipal(user.get());
    valid.put(key, principal);
    return principal;
  }

  /**
   * Drops a token from the caches (e.g. after logout).
   *
   * @param token the raw token
   */
  public void evict(String token) {
    String key = sha256(token);
    valid.invalidate(key);
    invalid.invalidate(key);
  }

  private static OAuth2AuthenticatedPrincipal toPrincipal(GitHubUser user) {
    Map<String, Object> attributes = new HashMap<>();
    attributes.put(ATTR_LOGIN, user.login());
    if (user.name() != null) {
      attributes.put(ATTR_NAME, user.name());
    }
    if (user.avatarUrl() != null) {
      attributes.put(ATTR_AVATAR_URL, user.avatarUrl());
    }
    return new DefaultOAuth2AuthenticatedPrincipal(user.ownerId(), attributes, AUTHORITIES);
  }

  private void count(String source, String outcome) {
    meters.counter("plantshop.auth.token_check", "source", source, "outcome", outcome).increment();
  }

  private static String sha256(String token) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
