package com.plantshop.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.plantshop.auth.config.AuthMode;
import com.plantshop.auth.config.AuthProperties;
import com.plantshop.auth.github.GitHubTokenIntrospector;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;

class TokenAuthenticationManagerResolverTest {

  private static final String ISSUER = "http://127.0.0.1:8080";

  private final JwtDecoder decoder = mock(JwtDecoder.class);
  private final GitHubTokenIntrospector introspector = mock(GitHubTokenIntrospector.class);

  @SuppressWarnings("unchecked")
  private static <T> ObjectProvider<T> provider(T value) {
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(value);
    return provider;
  }

  private TokenAuthenticationManagerResolver resolver(AuthMode... modes) {
    AuthProperties auth = new AuthProperties(Set.of(modes), null);
    return new TokenAuthenticationManagerResolver(auth, provider(decoder), provider(introspector));
  }

  private static String jwt(String issuer) throws Exception {
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader(JWSAlgorithm.HS256),
            new JWTClaimsSet.Builder().issuer(issuer).subject("shopper").build());
    jwt.sign(new MACSigner("x".repeat(32)));
    return jwt.serialize();
  }

  private static Authentication authenticate(
      TokenAuthenticationManagerResolver resolver, String token) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + token);
    return resolver.resolve(request).authenticate(new BearerTokenAuthenticationToken(token));
  }

  @Test
  void localIssuerJwt_isValidatedByLocalDecoder() throws Exception {
    String token = jwt(ISSUER);
    when(decoder.decode(token))
        .thenReturn(
            Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject("shopper")
                .claim("scope", "catalog.read")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build());

    Authentication auth = authenticate(resolver(AuthMode.GITHUB, AuthMode.LOCAL), token);

    assertThat(auth.getName()).isEqualTo("local:shopper");
    assertThat(auth.getAuthorities()).extracting(Object::toString).contains("SCOPE_catalog.read");
    verify(introspector, never()).introspect(any());
  }

  @Test
  void foreignJwtAndOpaqueTokens_goToGitHub() throws Exception {
    when(introspector.introspect(any()))
        .thenReturn(new DefaultOAuth2AuthenticatedPrincipal("github:1", Map.of("a", 1), List.of()));
    TokenAuthenticationManagerResolver resolver = resolver(AuthMode.GITHUB, AuthMode.LOCAL);

    assertThat(authenticate(resolver, jwt("https://evil.example")).getName()).isEqualTo("github:1");
    assertThat(authenticate(resolver, "gho_opaque").getName()).isEqualTo("github:1");
    assertThat(authenticate(resolver, "a.b.c").getName()).isEqualTo("github:1");
    verify(decoder, never()).decode(any());
  }

  @Test
  void localOnlyMode_rejectsNonLocalTokens() {
    assertThatThrownBy(() -> authenticate(resolver(AuthMode.LOCAL), "gho_opaque"))
        .isInstanceOf(InvalidBearerTokenException.class);
  }

  @Test
  void githubOnlyMode_neverUsesLocalDecoder() throws Exception {
    when(introspector.introspect(any()))
        .thenReturn(new DefaultOAuth2AuthenticatedPrincipal("github:1", Map.of("a", 1), List.of()));

    assertThat(authenticate(resolver(AuthMode.GITHUB), jwt(ISSUER)).getName())
        .isEqualTo("github:1");
    verify(decoder, never()).decode(any());
  }
}
