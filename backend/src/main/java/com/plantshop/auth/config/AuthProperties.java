package com.plantshop.auth.config;

import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.auth.*} settings.
 *
 * @param mode enabled token sources
 * @param local Local Authorization Server settings (used only in mode {@code local})
 */
@ConfigurationProperties("app.auth")
public record AuthProperties(Set<AuthMode> mode, Local local) {

  /**
   * Creates the properties with safe defaults.
   *
   * @param mode enabled token sources (defaults to GitHub only)
   * @param local Local Authorization Server settings
   */
  public AuthProperties {
    mode = mode == null || mode.isEmpty() ? Set.of(AuthMode.GITHUB) : Set.copyOf(mode);
    local = local == null ? new Local(null, null, null, null, null) : local;
  }

  /**
   * Returns whether the given token source is enabled.
   *
   * @param candidate the mode to test
   * @return {@code true} when enabled
   */
  public boolean has(AuthMode candidate) {
    return mode.contains(candidate);
  }

  /**
   * Local Authorization Server settings (dev/e2e only).
   *
   * @param issuer issuer URL placed in the {@code iss} claim
   * @param spaRedirectUri redirect URI of the {@code plant-spa} client
   * @param swaggerRedirectUri redirect URI of the {@code swagger-ui} client
   * @param userPassword password shared by all in-memory users
   * @param users in-memory users and the scopes each may receive
   */
  public record Local(
      String issuer,
      String spaRedirectUri,
      String swaggerRedirectUri,
      String userPassword,
      List<LocalUser> users) {

    /**
     * Applies defaults.
     *
     * @param issuer issuer URL
     * @param spaRedirectUri SPA redirect URI
     * @param swaggerRedirectUri Swagger UI redirect URI
     * @param userPassword shared password
     * @param users in-memory users
     */
    public Local {
      issuer = issuer == null ? "http://127.0.0.1:8080" : issuer;
      spaRedirectUri =
          spaRedirectUri == null ? "http://127.0.0.1:3000/auth/callback" : spaRedirectUri;
      swaggerRedirectUri =
          swaggerRedirectUri == null
              ? "http://127.0.0.1:8080/swagger-ui/oauth2-redirect.html"
              : swaggerRedirectUri;
      users = users == null ? List.of() : List.copyOf(users);
    }
  }

  /**
   * One in-memory user.
   *
   * @param username login name (also the JWT {@code sub})
   * @param scopes OAuth2 scopes this user may be granted
   */
  public record LocalUser(String username, List<String> scopes) {

    /**
     * Applies defaults.
     *
     * @param username login name
     * @param scopes allowed scopes
     */
    public LocalUser {
      scopes = scopes == null ? List.of() : List.copyOf(scopes);
    }
  }
}
