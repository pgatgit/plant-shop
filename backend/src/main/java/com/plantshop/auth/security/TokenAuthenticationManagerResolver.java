package com.plantshop.auth.security;

import com.nimbusds.jwt.JWTParser;
import com.plantshop.auth.config.AuthMode;
import com.plantshop.auth.config.AuthProperties;
import com.plantshop.auth.github.GitHubTokenIntrospector;
import jakarta.servlet.http.HttpServletRequest;
import java.text.ParseException;
import java.util.Collection;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.OpaqueTokenAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.stereotype.Component;

/**
 * Chooses how to validate a bearer token (docs/plan/04-backend-design.md §7): a JWT whose {@code
 * iss} is the Local AS goes to the in-process JWT decoder (dev/e2e only); anything else goes to the
 * GitHub introspector when GitHub mode is enabled. The issuer is only read for routing; the chosen
 * provider verifies everything.
 */
@Component
public final class TokenAuthenticationManagerResolver
    implements AuthenticationManagerResolver<HttpServletRequest> {

  private final BearerTokenResolver bearerTokens = new DefaultBearerTokenResolver();
  private final AuthenticationManager local;
  private final AuthenticationManager github;
  private final String localIssuer;

  /**
   * Creates the resolver.
   *
   * @param auth auth settings
   * @param localJwtDecoder the Local AS decoder, present only in dev/e2e with mode {@code local}
   * @param githubIntrospector the GitHub introspector, present only in mode {@code github}
   */
  public TokenAuthenticationManagerResolver(
      AuthProperties auth,
      ObjectProvider<JwtDecoder> localJwtDecoder,
      ObjectProvider<GitHubTokenIntrospector> githubIntrospector) {
    this.localIssuer = auth.local().issuer();
    JwtDecoder decoder = auth.has(AuthMode.LOCAL) ? localJwtDecoder.getIfAvailable() : null;
    this.local = decoder == null ? null : jwtManager(decoder);
    GitHubTokenIntrospector introspector =
        auth.has(AuthMode.GITHUB) ? githubIntrospector.getIfAvailable() : null;
    this.github =
        introspector == null
            ? null
            : new OpaqueTokenAuthenticationProvider(introspector)::authenticate;
  }

  @Override
  public AuthenticationManager resolve(HttpServletRequest request) {
    String token = bearerTokens.resolve(request);
    if (local != null && token != null && localIssuer.equals(issuerOf(token))) {
      return local;
    }
    if (github != null) {
      return github;
    }
    return authentication -> {
      throw new InvalidBearerTokenException("Invalid token");
    };
  }

  private static String issuerOf(String token) {
    if (token.chars().filter(c -> c == '.').count() != 2) {
      return null;
    }
    try {
      return JWTParser.parse(token).getJWTClaimsSet().getIssuer();
    } catch (ParseException e) {
      return null;
    }
  }

  private static AuthenticationManager jwtManager(JwtDecoder decoder) {
    JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
    Converter<Jwt, AbstractAuthenticationToken> converter =
        jwt -> {
          Collection<GrantedAuthority> authorities = scopes.convert(jwt);
          return new JwtAuthenticationToken(jwt, authorities, "local:" + jwt.getSubject());
        };
    JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
    provider.setJwtAuthenticationConverter(converter);
    return provider::authenticate;
  }
}
