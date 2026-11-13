package com.plantshop.support;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Drives the Local Authorization Server like the SPA and a browser would: Authorization Code + PKCE
 * through the form login, over real HTTP with a cookie jar and without following redirects.
 */
public final class LocalLogin {

  /** Tokens returned by the token endpoint. */
  public record Tokens(
      String accessToken, String idToken, String refreshToken, String corsAllowOrigin) {}

  /** The SPA origin and redirect URI registered for {@code plant-spa}. */
  public static final String SPA_ORIGIN = "http://127.0.0.1:3000";

  private static final String REDIRECT_URI = SPA_ORIGIN + "/auth/callback";
  private static final String SCOPE = "openid catalog.read cart.read cart.write";
  private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");
  private static final Pattern CODE = Pattern.compile("[?&]code=([^&]+)");

  private final String base;
  private final HttpClient http;
  private final JsonMapper json = JsonMapper.builder().build();
  private final SecureRandom random = new SecureRandom();

  /**
   * Creates a browser-like client with its own cookie jar.
   *
   * @param base backend base URL, e.g. {@code http://127.0.0.1:8080}
   */
  public LocalLogin(String base) {
    this.base = base;
    this.http =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  /**
   * Logs in and exchanges the code (the happy path).
   *
   * @param username in-memory user
   * @param password shared password
   * @return the tokens
   * @throws Exception on I/O failure or unexpected responses
   */
  public Tokens login(String username, String password) throws Exception {
    String verifier = newVerifier();
    String code = authorizeAndLogin(username, password, verifier);
    HttpResponse<String> response = token(code, verifier);
    if (response.statusCode() != 200) {
      throw new IllegalStateException("Token request failed: " + response.body());
    }
    JsonNode body = json.readTree(response.body());
    return new Tokens(
        body.path("access_token").asString(null),
        body.path("id_token").asString(null),
        body.path("refresh_token").asString(null),
        response.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
  }

  /**
   * Submits wrong credentials.
   *
   * @param username user
   * @param password wrong password
   * @return where the login form redirects to
   * @throws Exception on I/O failure
   */
  public String loginExpectingFailure(String username, String password) throws Exception {
    send(get(authorizeUrl(challenge(newVerifier()))));
    return submitLogin(username, password);
  }

  /**
   * Logs in, then exchanges the code with a different verifier.
   *
   * @param username user
   * @param password password
   * @return the token endpoint's error body
   * @throws Exception on I/O failure
   */
  public String loginWithWrongVerifier(String username, String password) throws Exception {
    String code = authorizeAndLogin(username, password, newVerifier());
    return token(code, newVerifier()).body();
  }

  /**
   * Sends an authorization request without {@code code_challenge} (session must exist).
   *
   * @return the redirect location
   * @throws Exception on I/O failure
   */
  public String authorizeWithoutPkce() throws Exception {
    Map<String, String> params = authorizeParams();
    return location(send(get(base + "/oauth2/authorize?" + form(params))));
  }

  /**
   * Sends a PKCE authorization request with the current session.
   *
   * @return the redirect location
   * @throws Exception on I/O failure
   */
  public String authorizeLocation() throws Exception {
    return location(send(get(authorizeUrl(challenge(newVerifier())))));
  }

  /**
   * Calls the OIDC RP-initiated logout endpoint.
   *
   * @param idToken the ID token hint
   * @return the redirect location
   * @throws Exception on I/O failure
   */
  public String logout(String idToken) throws Exception {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("id_token_hint", idToken);
    params.put("client_id", "plant-spa");
    params.put("post_logout_redirect_uri", SPA_ORIGIN + "/login");
    return location(send(get(base + "/connect/logout?" + form(params))));
  }

  private String authorizeAndLogin(String username, String password, String verifier)
      throws Exception {
    String toLogin = location(send(get(authorizeUrl(challenge(verifier)))));
    if (!toLogin.endsWith("/login")) {
      throw new IllegalStateException("Expected redirect to /login but got " + toLogin);
    }
    String savedRequest = submitLogin(username, password);
    String callback = location(send(get(resolve(savedRequest))));
    Matcher code = CODE.matcher(callback);
    if (!callback.startsWith(REDIRECT_URI) || !code.find()) {
      throw new IllegalStateException("Expected a code redirect but got " + callback);
    }
    return java.net.URLDecoder.decode(code.group(1), StandardCharsets.UTF_8);
  }

  private String submitLogin(String username, String password) throws Exception {
    String page = send(get(base + "/login")).body();
    Matcher csrf = CSRF.matcher(page);
    if (!csrf.find()) {
      throw new IllegalStateException("No CSRF token on the login page");
    }
    Map<String, String> params = new LinkedHashMap<>();
    params.put("username", username);
    params.put("password", password);
    params.put("_csrf", csrf.group(1));
    return location(send(postForm(base + "/login", params, null)));
  }

  private HttpResponse<String> token(String code, String verifier) throws Exception {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("grant_type", "authorization_code");
    params.put("code", code);
    params.put("redirect_uri", REDIRECT_URI);
    params.put("client_id", "plant-spa");
    params.put("code_verifier", verifier);
    return send(postForm(base + "/oauth2/token", params, SPA_ORIGIN));
  }

  private Map<String, String> authorizeParams() {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("response_type", "code");
    params.put("client_id", "plant-spa");
    params.put("redirect_uri", REDIRECT_URI);
    params.put("scope", SCOPE);
    params.put("state", newVerifier().substring(0, 16));
    return params;
  }

  private String authorizeUrl(String challenge) {
    Map<String, String> params = authorizeParams();
    params.put("code_challenge", challenge);
    params.put("code_challenge_method", "S256");
    return base + "/oauth2/authorize?" + form(params);
  }

  private HttpRequest get(String url) {
    return HttpRequest.newBuilder(URI.create(url)).header("Accept", "text/html").GET().build();
  }

  private HttpRequest postForm(String url, Map<String, String> params, String origin) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form(params)));
    if (origin != null) {
      builder.header("Origin", origin);
    }
    return builder.build();
  }

  private HttpResponse<String> send(HttpRequest request) throws Exception {
    return http.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private static String location(HttpResponse<String> response) {
    return response
        .headers()
        .firstValue("Location")
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Expected a redirect, got " + response.statusCode() + ": " + response.body()));
  }

  private String resolve(String location) {
    return URI.create(base + "/").resolve(location).toString();
  }

  private static String form(Map<String, String> params) {
    return params.entrySet().stream()
        .map(
            e ->
                URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                    + "="
                    + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
        .collect(Collectors.joining("&"));
  }

  private String newVerifier() {
    byte[] bytes = new byte[48];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String challenge(String verifier) throws Exception {
    byte[] digest =
        MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
  }
}
