package com.plantshop.auth.github;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.plantshop.support.GitHubStubs.CHECK_PATH;
import static com.plantshop.support.GitHubStubs.CLIENT_ID;
import static com.plantshop.support.GitHubStubs.CLIENT_SECRET;
import static com.plantshop.support.GitHubStubs.checkToken;
import static com.plantshop.support.GitHubStubs.tokenCheckStatus;
import static com.plantshop.support.GitHubStubs.validToken;
import static com.plantshop.support.OpenApiContract.conformsToContract;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.plantshop.support.MySqlTestcontainersConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * GitHub mode end to end (D1–D3, docs/plan/09-testing-strategy.md §3.4) with WireMock standing in
 * for github.com and api.github.com. Tokens travel in real Authorization headers through the real
 * resolver, introspector, cache and rate limiter.
 */
@SpringBootTest(
    properties = {
      "app.auth.mode=github",
      "app.github.read-timeout=500ms",
      "app.rate-limit.token-exchange.capacity=3"
    })
@AutoConfigureMockMvc
@Import(MySqlTestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
@Tag("US-01")
class GitHubAuthIT {

  @RegisterExtension
  static final WireMockExtension GITHUB =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private static final String VERIFIER = "v".repeat(64);
  private static final String SPA_ORIGIN = "http://127.0.0.1:3000";
  private static final String SPA_REDIRECT = SPA_ORIGIN + "/auth/callback";

  @DynamicPropertySource
  static void githubUrls(DynamicPropertyRegistry registry) {
    registry.add("app.github.oauth-base-url", GITHUB::baseUrl);
    registry.add("app.github.api-base-url", GITHUB::baseUrl);
  }

  @Autowired private MockMvc mvc;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping mappings;

  @BeforeEach
  void resetGitHub() {
    GITHUB.resetAll();
  }

  /** Each test uses its own client address so the per-IP rate limit never leaks between tests. */
  private static RequestPostProcessor fromNewClient() {
    String ip = "10.1." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
    return r -> {
      r.setRemoteAddr(ip);
      return r;
    };
  }

  private static MockHttpServletRequestBuilder tokenRequest(String clientId) {
    return post("/api/v1/auth/token")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .param("grant_type", "authorization_code")
        .param("code", "the-code")
        .param("redirect_uri", SPA_REDIRECT)
        .param("client_id", clientId)
        .param("code_verifier", VERIFIER);
  }

  private static String newToken() {
    return "gho_" + UUID.randomUUID().toString().replace("-", "");
  }

  // ---------------------------------------------------------------- token exchange (proxy)

  @Test
  @DisplayName("[D2] the proxy adds the client secret and returns GitHub's token")
  void exchange_success_addsSecretServerSide() throws Exception {
    GITHUB.stubFor(
        com.github.tomakehurst.wiremock.client.WireMock.post(
                urlEqualTo("/login/oauth/access_token"))
            .withRequestBody(containing("client_secret=" + CLIENT_SECRET))
            .withRequestBody(containing("code_verifier=" + VERIFIER))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"access_token\":\"gho_new\",\"token_type\":\"bearer\","
                            + "\"scope\":\"read:user\"}")));

    mvc.perform(tokenRequest(CLIENT_ID).with(fromNewClient()).header("Origin", SPA_ORIGIN))
        .andExpect(status().isOk())
        .andExpect(conformsToContract())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(header().string("Access-Control-Allow-Origin", SPA_ORIGIN))
        .andExpect(jsonPath("$.access_token").value("gho_new"))
        .andExpect(jsonPath("$.token_type").value("bearer"))
        .andExpect(jsonPath("$.client_secret").doesNotExist());
    GITHUB.verify(1, postRequestedFor(urlEqualTo("/login/oauth/access_token")));
  }

  @Test
  @DisplayName("GitHub rejecting the code (HTTP 200 + error) becomes invalid_grant")
  void exchange_badCode_isInvalidGrant() throws Exception {
    GITHUB.stubFor(
        com.github.tomakehurst.wiremock.client.WireMock.post(
                urlEqualTo("/login/oauth/access_token"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"error\":\"bad_verification_code\"}")));

    mvc.perform(tokenRequest(CLIENT_ID).with(fromNewClient()))
        .andExpect(status().isBadRequest())
        .andExpect(conformsToContract())
        .andExpect(jsonPath("$.error").value("invalid_grant"));
  }

  @Test
  void exchange_githubDown_is502() throws Exception {
    GITHUB.stubFor(
        com.github.tomakehurst.wiremock.client.WireMock.post(
                urlEqualTo("/login/oauth/access_token"))
            .willReturn(aResponse().withStatus(500)));

    mvc.perform(tokenRequest(CLIENT_ID).with(fromNewClient()))
        .andExpect(status().isBadGateway())
        .andExpect(conformsToContract())
        .andExpect(jsonPath("$.error").value("temporarily_unavailable"));
  }

  @Test
  void exchange_unknownClient_neverReachesGitHub() throws Exception {
    mvc.perform(tokenRequest("someone-else").with(fromNewClient()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_client"));
    GITHUB.verify(0, postRequestedFor(urlEqualTo("/login/oauth/access_token")));
  }

  @Test
  @DisplayName("[NFR-SEC-09] the token endpoint is rate limited per client with Retry-After")
  void exchange_rateLimited() throws Exception {
    RequestPostProcessor client = fromNewClient();
    for (int i = 0; i < 3; i++) {
      mvc.perform(tokenRequest("someone-else").with(client)).andExpect(status().isBadRequest());
    }

    mvc.perform(tokenRequest(CLIENT_ID).with(client).header("Origin", SPA_ORIGIN))
        .andExpect(status().isTooManyRequests())
        .andExpect(conformsToContract())
        .andExpect(header().exists("Retry-After"))
        .andExpect(header().string("Access-Control-Allow-Origin", SPA_ORIGIN))
        .andExpect(jsonPath("$.code").value("rate-limited"));
    mvc.perform(tokenRequest("someone-else").with(fromNewClient()))
        .andExpect(status().isBadRequest());
  }

  // ---------------------------------------------------------------- token validation

  @Test
  @DisplayName("[D3] a valid token authenticates as github:{id}; the check is cached")
  void validToken_authenticatesAndIsCached() throws Exception {
    String token = newToken();
    validToken(GITHUB, token, 583231, CLIENT_ID);

    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(conformsToContract())
        .andExpect(jsonPath("$.ownerId").value("github:583231"))
        .andExpect(jsonPath("$.displayName").value("Octo Cat"))
        .andExpect(jsonPath("$.provider").value("github"));
    mvc.perform(get("/api/v1/products").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(28));

    GITHUB.verify(1, postRequestedFor(urlEqualTo(CHECK_PATH)));
  }

  @Test
  @DisplayName("[NFR-SEC-07] unknown tokens get 401 invalid_token and are negatively cached")
  void unknownToken_is401AndCached() throws Exception {
    String token = newToken();
    tokenCheckStatus(GITHUB, token, 404);

    for (int i = 0; i < 2; i++) {
      mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
          .andExpect(status().isUnauthorized())
          .andExpect(conformsToContract())
          .andExpect(header().string("WWW-Authenticate", containsString("invalid_token")))
          .andExpect(jsonPath("$.code").value("unauthenticated"));
    }
    GITHUB.verify(1, postRequestedFor(urlEqualTo(CHECK_PATH)));
  }

  @Test
  void unprocessableToken_is401() throws Exception {
    String token = newToken();
    tokenCheckStatus(GITHUB, token, 422);

    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("[T1] a token GitHub attributes to another OAuth App is rejected")
  void foreignAppToken_is401() throws Exception {
    String token = newToken();
    validToken(GITHUB, token, 1, "another-app");

    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("[NFR-REL-05] a GitHub timeout is 503 auth-provider-unavailable with Retry-After")
  void githubTimeout_is503() throws Exception {
    String token = newToken();
    GITHUB.stubFor(checkToken(token).willReturn(aResponse().withStatus(200).withFixedDelay(1500)));

    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isServiceUnavailable())
        .andExpect(conformsToContract())
        .andExpect(header().string("Retry-After", "30"))
        .andExpect(jsonPath("$.code").value("auth-provider-unavailable"));
  }

  @Test
  void githubRateLimited_is503() throws Exception {
    String token = newToken();
    tokenCheckStatus(GITHUB, token, 403);

    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("auth-provider-unavailable"));
  }

  // ---------------------------------------------------------------- CORS and anonymous access

  @Test
  @DisplayName("[NFR-SEC-06] CORS preflight: allowed origin passes, others are refused")
  void cors_allowsOnlyConfiguredOrigin() throws Exception {
    mvc.perform(
            options("/api/v1/products")
                .header("Origin", SPA_ORIGIN)
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", SPA_ORIGIN))
        .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));

    mvc.perform(
            options("/api/v1/products")
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
  }

  @Test
  @DisplayName("[NFR-SEC-01] every API endpoint except the token proxy requires a token")
  void everyEndpoint_requiresAuthentication() throws Exception {
    List<String> checked = new ArrayList<>();
    for (var entry : mappings.getHandlerMethods().entrySet()) {
      RequestMappingInfo info = entry.getKey();
      HandlerMethod handler = entry.getValue();
      Set<String> patterns = info.getPatternValues();
      Set<org.springframework.web.bind.annotation.RequestMethod> methods =
          info.getMethodsCondition().getMethods();
      for (String pattern : patterns) {
        if (!pattern.startsWith("/api/") || pattern.equals("/api/v1/auth/token")) {
          continue;
        }
        String path = pattern.replaceAll("\\{[^}]+}", "1");
        for (var method : methods.isEmpty() ? Set.of(HttpMethod.GET) : toHttp(methods)) {
          mvc.perform(request(method, path).contentType(MediaType.APPLICATION_JSON).content("{}"))
              .andExpect(status().isUnauthorized());
          checked.add(method + " " + path + " (" + handler.getMethod().getName() + ")");
        }
      }
    }
    assertThat(checked).isNotEmpty().anyMatch(s -> s.startsWith("GET /api/v1/products"));
  }

  private static Set<HttpMethod> toHttp(
      Set<org.springframework.web.bind.annotation.RequestMethod> methods) {
    Set<HttpMethod> result = new java.util.HashSet<>();
    methods.forEach(m -> result.add(HttpMethod.valueOf(m.name())));
    return result;
  }

  // ---------------------------------------------------------------- secrets never logged

  @Test
  @DisplayName("[NFR-SEC-03][NFR-SEC-05] secrets, codes and tokens never appear in logs")
  void secretsAndTokens_areNeverLogged(CapturedOutput output) throws Exception {
    String token = newToken();
    tokenCheckStatus(GITHUB, token, 403);
    GITHUB.stubFor(
        com.github.tomakehurst.wiremock.client.WireMock.post(
                urlEqualTo("/login/oauth/access_token"))
            .willReturn(aResponse().withStatus(500)));

    mvc.perform(tokenRequest(CLIENT_ID).with(fromNewClient()));
    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token));

    assertThat(output.getAll())
        .doesNotContain(CLIENT_SECRET)
        .doesNotContain(token)
        .doesNotContain(VERIFIER)
        .doesNotContain("the-code");
  }

  private static org.hamcrest.Matcher<String> containsString(String s) {
    return org.hamcrest.Matchers.containsString(s);
  }
}
