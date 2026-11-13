package com.plantshop.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.opaqueToken;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * MockMvc authentication helpers producing the same authentication types as production: a Local AS
 * JWT ({@code JwtAuthenticationToken}) or a GitHub opaque token ({@code
 * BearerTokenAuthentication}). Token validation itself is covered by the auth integration tests.
 */
public final class TestAuth {

  private TestAuth() {}

  /**
   * A local shopper holding every API scope.
   *
   * @return request post-processor
   */
  public static RequestPostProcessor shopper() {
    return withScopes("shopper", "catalog.read", "cart.read", "cart.write");
  }

  /**
   * A local principal with exactly the given scopes.
   *
   * @param username the JWT subject
   * @param scopes OAuth2 scopes (without the {@code SCOPE_} prefix)
   * @return request post-processor
   */
  public static RequestPostProcessor withScopes(String username, String... scopes) {
    List<GrantedAuthority> authorities =
        Arrays.stream(scopes)
            .<GrantedAuthority>map(s -> new SimpleGrantedAuthority("SCOPE_" + s))
            .toList();
    return jwt()
        .jwt(j -> j.subject(username).claim("scope", String.join(" ", scopes)))
        .authorities(authorities);
  }

  /**
   * A GitHub user as produced by {@code GitHubTokenIntrospector}.
   *
   * @param id GitHub user id
   * @param login GitHub login
   * @return request post-processor
   */
  public static RequestPostProcessor githubUser(long id, String login) {
    List<GrantedAuthority> authorities =
        List.of(
            new SimpleGrantedAuthority("SCOPE_catalog.read"),
            new SimpleGrantedAuthority("SCOPE_cart.read"),
            new SimpleGrantedAuthority("SCOPE_cart.write"));
    return opaqueToken()
        .principal(
            new DefaultOAuth2AuthenticatedPrincipal(
                "github:" + id,
                Map.of("login", login, "avatar_url", "https://avatars.example/" + id),
                authorities));
  }
}
