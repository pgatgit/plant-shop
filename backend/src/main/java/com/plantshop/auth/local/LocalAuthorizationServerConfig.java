package com.plantshop.auth.local;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.plantshop.auth.config.AuthMode;
import com.plantshop.auth.config.AuthProperties;
import com.plantshop.auth.config.ConditionalOnAuthMode;
import com.plantshop.auth.security.Scopes;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

/**
 * Local Authorization Server for dev and e2e (D4, ADR-003): Spring Authorization Server with
 * in-memory users, two public PKCE clients and 15-minute RS256 access tokens. It never exists in
 * {@code prod} ({@code AuthStartupValidator} also rejects mode {@code local} there).
 */
@Configuration(proxyBeanMethods = false)
@Profile({"dev", "e2e"})
@ConditionalOnAuthMode(AuthMode.LOCAL)
public class LocalAuthorizationServerConfig {

  /** Audience every Local AS access token is issued for and the API requires. */
  public static final String AUDIENCE = "plant-api";

  /** Client id of the SPA. */
  public static final String SPA_CLIENT_ID = "plant-spa";

  /** Client id of Swagger UI. */
  public static final String SWAGGER_CLIENT_ID = "swagger-ui";

  /** Access token lifetime (docs/plan/07-auth-design.md §3). */
  static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);

  /** Authority prefix marking which scopes an in-memory user may receive. */
  static final String ALLOWED_SCOPE_PREFIX = "ALLOW_";

  private static final List<String> API_SCOPES =
      List.of(Scopes.CATALOG_READ, Scopes.CART_READ, Scopes.CART_WRITE);

  /**
   * Authorization Server protocol endpoints ({@code /oauth2/**}, OIDC discovery, logout).
   * Unauthenticated browser requests are sent to the login form.
   *
   * @param http the security builder
   * @return the chain
   * @throws Exception if the configuration is invalid
   */
  @Bean
  @Order(1)
  public SecurityFilterChain localAuthorizationServerSecurityFilterChain(HttpSecurity http)
      throws Exception {
    http.oauth2AuthorizationServer(
            as -> {
              http.securityMatcher(as.getEndpointsMatcher());
              as.oidc(Customizer.withDefaults());
            })
        .cors(Customizer.withDefaults())
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
        .exceptionHandling(
            e ->
                e.defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint("/login"),
                    new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
    return http.build();
  }

  /**
   * Form login for the in-memory users. Session based with CSRF protection.
   *
   * @param http the security builder
   * @return the chain
   * @throws Exception if the configuration is invalid
   */
  @Bean
  @Order(5)
  public SecurityFilterChain localLoginSecurityFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/login", "/logout", "/default-ui.css")
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .formLogin(Customizer.withDefaults());
    return http.build();
  }

  /**
   * In-memory users from {@code app.auth.local.users}; each gets {@code ALLOW_<scope>} authorities
   * listing the scopes it may be granted.
   *
   * @param auth auth settings (users and shared password)
   * @return the user store
   */
  @Bean
  public UserDetailsService localUsers(AuthProperties auth) {
    PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    String encoded = encoder.encode(auth.local().userPassword());
    List<UserDetails> users = new ArrayList<>();
    for (AuthProperties.LocalUser user : auth.local().users()) {
      List<GrantedAuthority> authorities = new ArrayList<>();
      authorities.add(new SimpleGrantedAuthority("ROLE_SHOPPER"));
      user.scopes()
          .forEach(s -> authorities.add(new SimpleGrantedAuthority(ALLOWED_SCOPE_PREFIX + s)));
      users.add(
          User.withUsername(user.username()).password(encoded).authorities(authorities).build());
    }
    return new InMemoryUserDetailsManager(users);
  }

  /**
   * The two public clients. PKCE is mandatory, consent is skipped (first-party clients), and no
   * refresh tokens are issued to public clients.
   *
   * @param auth auth settings (redirect URIs)
   * @return the client repository
   */
  @Bean
  public RegisteredClientRepository localRegisteredClients(AuthProperties auth) {
    return new InMemoryRegisteredClientRepository(
        publicClient(
            SPA_CLIENT_ID,
            auth.local().spaRedirectUri(),
            postLogoutRedirect(auth.local().spaRedirectUri())),
        publicClient(SWAGGER_CLIENT_ID, auth.local().swaggerRedirectUri(), null));
  }

  private static String postLogoutRedirect(String spaRedirectUri) {
    return spaRedirectUri.replaceFirst("/auth/callback$", "/login");
  }

  private static RegisteredClient publicClient(
      String clientId, String redirectUri, String postLogoutRedirectUri) {
    RegisteredClient.Builder builder =
        RegisteredClient.withId(
                UUID.nameUUIDFromBytes(clientId.getBytes(StandardCharsets.UTF_8)).toString())
            .clientId(clientId)
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(redirectUri)
            .scope(OidcScopes.OPENID)
            .scopes(s -> s.addAll(API_SCOPES))
            .clientSettings(
                ClientSettings.builder()
                    .requireProofKey(true)
                    .requireAuthorizationConsent(false)
                    .build())
            .tokenSettings(
                TokenSettings.builder()
                    .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                    .accessTokenTimeToLive(ACCESS_TOKEN_TTL)
                    .build());
    if (postLogoutRedirectUri != null) {
      builder.postLogoutRedirectUri(postLogoutRedirectUri);
    }
    return builder.build();
  }

  /**
   * Issuer and endpoint paths.
   *
   * @param auth auth settings (issuer)
   * @return the settings
   */
  @Bean
  public AuthorizationServerSettings localAuthorizationServerSettings(AuthProperties auth) {
    return AuthorizationServerSettings.builder().issuer(auth.local().issuer()).build();
  }

  /**
   * RSA signing key generated at start-up: tokens become invalid after a restart, which is
   * acceptable for dev/e2e.
   *
   * @return the key source
   */
  @Bean
  public JWKSource<SecurityContext> localJwkSource() {
    KeyPair keyPair = generateRsaKey();
    RSAKey rsaKey =
        new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
            .privateKey((RSAPrivateKey) keyPair.getPrivate())
            .keyID(UUID.randomUUID().toString())
            .build();
    return new ImmutableJWKSet<>(new JWKSet(rsaKey));
  }

  /**
   * Validates Local AS access tokens in-process (no HTTP): signature, expiry, issuer and audience.
   *
   * @param jwkSource the signing keys
   * @param auth auth settings (issuer)
   * @return the decoder used by {@code TokenAuthenticationManagerResolver}
   */
  @Bean
  public JwtDecoder localJwtDecoder(JWKSource<SecurityContext> jwkSource, AuthProperties auth) {
    NimbusJwtDecoder decoder =
        (NimbusJwtDecoder) OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    OAuth2TokenValidator<Jwt> audience =
        new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(AUDIENCE));
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(auth.local().issuer()), audience));
    return decoder;
  }

  /**
   * Adds the API audience to access tokens and narrows the {@code scope} claim to the scopes the
   * user is allowed (e.g. {@code viewer} only ever receives {@code catalog.read}).
   *
   * @return the customizer
   */
  @Bean
  public OAuth2TokenCustomizer<JwtEncodingContext> localAccessTokenCustomizer() {
    return context -> {
      if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
        return;
      }
      Authentication user = context.getPrincipal();
      if (user == null) {
        return;
      }
      Set<String> allowed = new LinkedHashSet<>();
      for (GrantedAuthority authority : user.getAuthorities()) {
        String name = authority.getAuthority();
        if (name != null && name.startsWith(ALLOWED_SCOPE_PREFIX)) {
          allowed.add(name.substring(ALLOWED_SCOPE_PREFIX.length()));
        }
      }
      Set<String> granted = new LinkedHashSet<>(context.getAuthorizedScopes());
      granted.removeIf(scope -> !OidcScopes.OPENID.equals(scope) && !allowed.contains(scope));
      context.getClaims().audience(List.of(AUDIENCE)).claim("scope", granted);
    };
  }

  private static KeyPair generateRsaKey() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("RSA is not available", e);
    }
  }
}
