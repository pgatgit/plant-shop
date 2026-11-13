package com.plantshop.auth.github;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.github.*} settings. Base URLs are configurable so tests can point them at WireMock.
 *
 * @param clientId OAuth App client id (public)
 * @param clientSecret OAuth App client secret (never logged or returned)
 * @param allowedRedirectUris redirect URIs the token-exchange proxy accepts (exact match)
 * @param oauthBaseUrl base of the OAuth endpoints ({@code https://github.com})
 * @param apiBaseUrl base of the REST API ({@code https://api.github.com})
 * @param tokenCacheTtl how long a valid token check is cached
 * @param negativeCacheTtl how long an invalid token is remembered
 * @param connectTimeout connect timeout for GitHub calls
 * @param readTimeout read timeout for GitHub calls
 */
@ConfigurationProperties("app.github")
public record GitHubProperties(
    String clientId,
    String clientSecret,
    List<String> allowedRedirectUris,
    URI oauthBaseUrl,
    URI apiBaseUrl,
    Duration tokenCacheTtl,
    Duration negativeCacheTtl,
    Duration connectTimeout,
    Duration readTimeout) {

  /**
   * Applies defaults.
   *
   * @param clientId client id
   * @param clientSecret client secret
   * @param allowedRedirectUris allowed redirect URIs
   * @param oauthBaseUrl OAuth base URL
   * @param apiBaseUrl API base URL
   * @param tokenCacheTtl positive cache TTL
   * @param negativeCacheTtl negative cache TTL
   * @param connectTimeout connect timeout
   * @param readTimeout read timeout
   */
  public GitHubProperties {
    allowedRedirectUris =
        allowedRedirectUris == null ? List.of() : List.copyOf(allowedRedirectUris);
    oauthBaseUrl = oauthBaseUrl == null ? URI.create("https://github.com") : oauthBaseUrl;
    apiBaseUrl = apiBaseUrl == null ? URI.create("https://api.github.com") : apiBaseUrl;
    tokenCacheTtl = tokenCacheTtl == null ? Duration.ofMinutes(5) : tokenCacheTtl;
    negativeCacheTtl = negativeCacheTtl == null ? Duration.ofSeconds(30) : negativeCacheTtl;
    connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
    readTimeout = readTimeout == null ? Duration.ofSeconds(3) : readTimeout;
  }

  @Override
  public String toString() {
    return "GitHubProperties[clientId="
        + clientId
        + ", clientSecret=***, oauthBaseUrl="
        + oauthBaseUrl
        + ", apiBaseUrl="
        + apiBaseUrl
        + "]";
  }
}
