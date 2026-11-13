package com.plantshop.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CurrentUserResolverTest {

  private final CurrentUserResolver resolver = new CurrentUserResolver();

  private static BearerTokenAuthentication github(Map<String, Object> attributes) {
    DefaultOAuth2AuthenticatedPrincipal principal =
        new DefaultOAuth2AuthenticatedPrincipal("github:7", attributes, List.of());
    OAuth2AccessToken token =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "gho",
            Instant.now(),
            Instant.now().plusSeconds(60));
    return new BearerTokenAuthentication(principal, token, List.of());
  }

  @Test
  void localJwt_mapsToLocalOwner() {
    Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("shopper").build();

    assertThat(resolver.resolve(new JwtAuthenticationToken(jwt)))
        .isEqualTo(new CurrentUser("local:shopper", "shopper", null, "local"));
  }

  @Test
  void githubToken_usesNameAndAvatar() {
    assertThat(resolver.resolve(github(Map.of("login", "oct", "name", "Octo", "avatar_url", "a"))))
        .isEqualTo(new CurrentUser("github:7", "Octo", "a", "github"));
  }

  @Test
  void githubTokenWithoutName_fallsBackToLogin() {
    assertThat(resolver.resolve(github(Map.of("login", "oct"))))
        .isEqualTo(new CurrentUser("github:7", "oct", null, "github"));
  }

  @Test
  void otherAuthentication_isRejected() {
    assertThatThrownBy(
            () -> resolver.resolve(new UsernamePasswordAuthenticationToken("u", "p", List.of())))
        .isInstanceOf(InsufficientAuthenticationException.class);
  }
}
