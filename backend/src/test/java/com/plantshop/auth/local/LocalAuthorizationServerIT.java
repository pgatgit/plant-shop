package com.plantshop.auth.local;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.plantshop.support.LocalLogin;
import com.plantshop.support.LocalLogin.Tokens;
import com.plantshop.support.MySqlTestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * The dev/e2e Local Authorization Server over real HTTP (D4, ADR-003): Authorization Code + PKCE
 * through the login form, JWT claims, scope narrowing per user, token validation by the API, and
 * logout.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Import(MySqlTestcontainersConfiguration.class)
@Tag("US-01")
class LocalAuthorizationServerIT {

  private static final String ISSUER = "http://127.0.0.1:8080";

  @Value("${local.server.port}")
  private int port;

  @Autowired private JWKSource<SecurityContext> jwkSource;

  private LocalLogin login;
  private final HttpClient http = HttpClient.newHttpClient();

  @BeforeEach
  void setUp() {
    login = new LocalLogin("http://127.0.0.1:" + port);
  }

  private HttpResponse<String> api(String path, String accessToken) throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  @Test
  @DisplayName("[AC-01.3] PKCE login issues a 15-minute JWT for plant-api with the user's scopes")
  void shopperLogin_issuesExpectedAccessToken() throws Exception {
    Tokens tokens = login.login("shopper", "plants-dev-only");

    JWTClaimsSet claims = SignedJWT.parse(tokens.accessToken()).getJWTClaimsSet();
    assertThat(claims.getIssuer()).isEqualTo(ISSUER);
    assertThat(claims.getSubject()).isEqualTo("shopper");
    assertThat(claims.getAudience()).containsExactly("plant-api");
    assertThat(claims.getStringListClaim("scope"))
        .containsExactlyInAnyOrder("openid", "catalog.read", "cart.read", "cart.write");
    assertThat(
            Duration.between(
                claims.getIssueTime().toInstant(), claims.getExpirationTime().toInstant()))
        .isEqualTo(Duration.ofMinutes(15));
    assertThat(tokens.idToken()).isNotBlank();
    assertThat(tokens.refreshToken()).as("no refresh tokens for public clients").isNull();
    assertThat(tokens.corsAllowOrigin()).isEqualTo("http://127.0.0.1:3000");
  }

  @Test
  @DisplayName("[AC-01.3][AC-01.6] the API accepts the Local AS token")
  void localToken_isAcceptedByApi() throws Exception {
    String token = login.login("shopper", "plants-dev-only").accessToken();

    HttpResponse<String> me = api("/api/v1/me", token);
    assertThat(me.statusCode()).isEqualTo(200);
    assertThat(me.body())
        .contains("\"ownerId\":\"local:shopper\"")
        .contains("\"provider\":\"local\"");
    assertThat(api("/api/v1/products", token).statusCode()).isEqualTo(200);
  }

  @Test
  @DisplayName("[NFR-SEC-02] viewer only ever receives catalog.read, so cart access is forbidden")
  void viewer_getsCatalogScopeOnly() throws Exception {
    String token = login.login("viewer", "plants-dev-only").accessToken();

    assertThat(SignedJWT.parse(token).getJWTClaimsSet().getStringListClaim("scope"))
        .containsExactlyInAnyOrder("openid", "catalog.read");
    assertThat(api("/api/v1/products", token).statusCode()).isEqualTo(200);
    HttpResponse<String> cart = api("/api/v1/cart", token);
    assertThat(cart.statusCode()).isEqualTo(403);
    assertThat(cart.body()).contains("\"code\":\"forbidden\"");
  }

  @Test
  void wrongPassword_returnsToLoginWithError() throws Exception {
    assertThat(login.loginExpectingFailure("shopper", "wrong")).contains("/login?error");
  }

  @Test
  @DisplayName("[07 §4] PKCE is mandatory for public clients")
  void authorizeWithoutPkce_isRejected() throws Exception {
    login.login("shopper", "plants-dev-only");

    assertThat(login.authorizeWithoutPkce()).contains("error=invalid_request");
  }

  @Test
  void wrongVerifier_isInvalidGrant() throws Exception {
    assertThat(login.loginWithWrongVerifier("shopper", "plants-dev-only"))
        .contains("invalid_grant");
  }

  @Test
  @DisplayName("[AC-02.3] logout ends the AS session and redirects back to the SPA")
  void logout_endsSession() throws Exception {
    Tokens tokens = login.login("shopper", "plants-dev-only");

    assertThat(login.logout(tokens.idToken())).isEqualTo("http://127.0.0.1:3000/login");
    assertThat(login.authorizeLocation()).contains("/login");
  }

  @Test
  @DisplayName("[T6] expired, wrong-audience and foreign-key tokens are rejected")
  void tamperedTokens_areRejected() throws Exception {
    NimbusJwtEncoder encoder = new NimbusJwtEncoder(jwkSource);
    Instant now = Instant.now();
    JwtClaimsSet.Builder valid =
        JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject("shopper")
            .audience(List.of("plant-api"))
            .claim("scope", List.of("catalog.read"))
            .issuedAt(now)
            .expiresAt(now.plusSeconds(600));
    JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();

    String good = encoder.encode(JwtEncoderParameters.from(header, valid.build())).getTokenValue();
    String expired =
        encoder
            .encode(
                JwtEncoderParameters.from(
                    header,
                    valid
                        .issuedAt(now.minusSeconds(3600))
                        .expiresAt(now.minusSeconds(1800))
                        .build()))
            .getTokenValue();
    valid.issuedAt(now).expiresAt(now.plusSeconds(600));
    String wrongAudience =
        encoder
            .encode(JwtEncoderParameters.from(header, valid.audience(List.of("other")).build()))
            .getTokenValue();
    LocalAuthorizationServerConfig otherServer = new LocalAuthorizationServerConfig();
    String foreignKey =
        new NimbusJwtEncoder(otherServer.localJwkSource())
            .encode(JwtEncoderParameters.from(header, valid.audience(List.of("plant-api")).build()))
            .getTokenValue();

    assertThat(api("/api/v1/products", good).statusCode()).isEqualTo(200);
    assertThat(api("/api/v1/products", expired).statusCode()).isEqualTo(401);
    assertThat(api("/api/v1/products", wrongAudience).statusCode()).isEqualTo(401);
    assertThat(api("/api/v1/products", foreignKey).statusCode()).isEqualTo(401);
  }
}
