package com.plantshop.auth.github;

import java.net.http.HttpClient;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Thin client for the two GitHub calls the backend makes (docs/plan/07-auth-design.md §2). Error
 * messages never include tokens, codes or the client secret.
 */
public class GitHubClient {

  private static final String API_VERSION = "2022-11-28";

  private final GitHubProperties properties;
  private final RestClient oauth;
  private final RestClient api;

  /**
   * Creates the client.
   *
   * @param properties GitHub settings (base URLs, credentials, timeouts)
   */
  public GitHubClient(GitHubProperties properties) {
    this.properties = properties;
    HttpClient http =
        HttpClient.newBuilder()
            // A handful of small calls: HTTP/1.1 avoids h2c upgrade quirks on plain-HTTP test
            // servers and costs nothing against GitHub.
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(properties.connectTimeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(properties.readTimeout());
    this.oauth =
        RestClient.builder().requestFactory(factory).baseUrl(properties.oauthBaseUrl()).build();
    this.api =
        RestClient.builder()
            .requestFactory(factory)
            .baseUrl(properties.apiBaseUrl())
            .defaultHeader("X-GitHub-Api-Version", API_VERSION)
            .build();
  }

  /**
   * Exchanges an authorization code for a GitHub access token, adding the client secret.
   *
   * @param code the authorization code
   * @param redirectUri the redirect URI used in the authorization request
   * @param codeVerifier the PKCE code verifier
   * @return GitHub's token response ({@code access_token}, {@code token_type}, {@code scope})
   * @throws GitHubOAuthException when GitHub rejects the code
   * @throws GitHubUnavailableException when GitHub cannot be reached or misbehaves
   */
  public Map<String, Object> exchangeCode(String code, String redirectUri, String codeVerifier) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("client_id", properties.clientId());
    form.add("client_secret", properties.clientSecret());
    form.add("code", code);
    form.add("redirect_uri", redirectUri);
    form.add("code_verifier", codeVerifier);
    Map<String, Object> body;
    try {
      body =
          oauth
              .post()
              .uri("/login/oauth/access_token")
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .accept(MediaType.APPLICATION_JSON)
              .body(form)
              .retrieve()
              .body(new org.springframework.core.ParameterizedTypeReference<>() {});
    } catch (RestClientException e) {
      throw new GitHubUnavailableException("GitHub code exchange failed", e);
    }
    if (body == null) {
      throw new GitHubUnavailableException("GitHub code exchange returned no body", null);
    }
    if (body.get("error") instanceof String error) {
      throw new GitHubOAuthException(error);
    }
    if (!(body.get("access_token") instanceof String)) {
      throw new GitHubUnavailableException("GitHub code exchange returned no access token", null);
    }
    return body;
  }

  /**
   * Checks a token with GitHub's "check a token" API, which only succeeds for tokens issued to our
   * OAuth App (threat T1).
   *
   * @param accessToken the token to check
   * @return the token's user, or empty when the token is invalid, revoked or foreign
   * @throws GitHubUnavailableException when GitHub cannot be reached or misbehaves
   */
  public Optional<GitHubUser> checkToken(String accessToken) {
    CheckTokenResponse response;
    try {
      response =
          api.post()
              .uri("/applications/{clientId}/token", properties.clientId())
              .headers(h -> h.setBasicAuth(properties.clientId(), properties.clientSecret()))
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.parseMediaType("application/vnd.github+json"))
              .body(Map.of("access_token", accessToken))
              .retrieve()
              .body(CheckTokenResponse.class);
    } catch (RestClientResponseException e) {
      HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
      if (status == HttpStatus.NOT_FOUND || status == HttpStatus.UNPROCESSABLE_CONTENT) {
        return Optional.empty();
      }
      throw new GitHubUnavailableException(
          "GitHub token check failed with status " + e.getStatusCode().value(), e);
    } catch (RestClientException e) {
      throw new GitHubUnavailableException("GitHub token check failed", e);
    }
    if (response == null || response.user() == null || response.app() == null) {
      throw new GitHubUnavailableException("GitHub token check returned an incomplete body", null);
    }
    if (!properties.clientId().equals(response.app().clientId())) {
      return Optional.empty();
    }
    CheckTokenResponse.User user = response.user();
    return Optional.of(new GitHubUser(user.id(), user.login(), user.name(), user.avatarUrl()));
  }
}
