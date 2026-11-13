package com.plantshop.auth.github;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.util.MultiValueMap;

/**
 * Validates an OAuth 2.0 token request from the SPA or Swagger UI and forwards it to GitHub with
 * the client secret (D2, docs/plan/07-auth-design.md §2 step 3). Errors follow RFC 6749 §5.2 so
 * that standard OAuth clients understand them.
 */
public class TokenExchangeService {

  /** RFC 7636 §4.1: 43–128 characters from the unreserved set. */
  private static final Pattern CODE_VERIFIER = Pattern.compile("^[A-Za-z0-9\\-._~]{43,128}$");

  private static final List<String> FIELDS =
      List.of("grant_type", "code", "redirect_uri", "client_id", "code_verifier");

  private final GitHubClient client;
  private final GitHubProperties properties;
  private final MeterRegistry meters;

  /**
   * Creates the service.
   *
   * @param client GitHub client
   * @param properties allowed client id and redirect URIs
   * @param meters metrics registry
   */
  public TokenExchangeService(
      GitHubClient client, GitHubProperties properties, MeterRegistry meters) {
    this.client = client;
    this.properties = properties;
    this.meters = meters;
  }

  /**
   * Exchanges an authorization code for a GitHub access token.
   *
   * @param form the form-encoded token request
   * @return the token response for the client
   * @throws OAuthErrorException for invalid requests or codes rejected by GitHub
   * @throws GitHubUnavailableException when GitHub cannot be reached
   */
  public Map<String, Object> exchange(MultiValueMap<String, String> form) {
    for (String field : FIELDS) {
      List<String> values = form.get(field);
      if (values != null && values.size() > 1) {
        throw fail(OAuthErrorException.invalidRequest("Parameter " + field + " is repeated."));
      }
    }
    String grantType = form.getFirst("grant_type");
    if (!"authorization_code".equals(grantType)) {
      throw fail(
          new OAuthErrorException(
              "unsupported_grant_type", "Only grant_type=authorization_code is supported."));
    }
    if (!properties.clientId().equals(form.getFirst("client_id"))) {
      throw fail(new OAuthErrorException("invalid_client", "Unknown client_id."));
    }
    String redirectUri = form.getFirst("redirect_uri");
    if (redirectUri == null || !properties.allowedRedirectUris().contains(redirectUri)) {
      throw fail(OAuthErrorException.invalidRequest("redirect_uri is not registered."));
    }
    String code = form.getFirst("code");
    if (code == null || code.isBlank() || code.length() > 512) {
      throw fail(OAuthErrorException.invalidRequest("code is required."));
    }
    String verifier = form.getFirst("code_verifier");
    if (verifier == null || !CODE_VERIFIER.matcher(verifier).matches()) {
      throw fail(
          OAuthErrorException.invalidRequest(
              "code_verifier must be 43-128 characters [A-Za-z0-9-._~] (RFC 7636)."));
    }
    Map<String, Object> github;
    try {
      github = client.exchangeCode(code, redirectUri, verifier);
    } catch (GitHubOAuthException e) {
      throw fail(
          new OAuthErrorException(
              "invalid_grant", "The authorization code is invalid or has expired."));
    } catch (GitHubUnavailableException e) {
      count("unavailable");
      throw e;
    }
    count("success");
    return Map.of(
        "access_token", github.get("access_token"),
        "token_type", "bearer",
        "scope", github.getOrDefault("scope", ""));
  }

  private OAuthErrorException fail(OAuthErrorException e) {
    count(e.error());
    return e;
  }

  private void count(String outcome) {
    meters.counter("plantshop.auth.token_exchange", "outcome", outcome).increment();
  }
}
