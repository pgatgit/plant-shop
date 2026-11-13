package com.plantshop.auth.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@ExtendWith(MockitoExtension.class)
@Tag("US-01")
class TokenExchangeServiceTest {

  private static final String VERIFIER = "a".repeat(43);
  private static final String REDIRECT = "http://127.0.0.1:3000/auth/callback";

  @Mock private GitHubClient client;

  private TokenExchangeService service() {
    return new TokenExchangeService(
        client,
        new GitHubProperties(
            "cid", "secret", List.of(REDIRECT), null, null, null, null, null, null),
        new SimpleMeterRegistry());
  }

  private static MultiValueMap<String, String> form() {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "authorization_code");
    form.add("code", "the-code");
    form.add("redirect_uri", REDIRECT);
    form.add("client_id", "cid");
    form.add("code_verifier", VERIFIER);
    return form;
  }

  @Test
  void validRequest_returnsBearerTokenFromGitHub() {
    when(client.exchangeCode("the-code", REDIRECT, VERIFIER))
        .thenReturn(Map.of("access_token", "gho_x", "token_type", "bearer", "scope", "read:user"));

    assertThat(service().exchange(form()))
        .containsEntry("access_token", "gho_x")
        .containsEntry("token_type", "bearer")
        .containsEntry("scope", "read:user");
  }

  @Test
  void missingScopeFromGitHub_defaultsToEmpty() {
    when(client.exchangeCode(any(), any(), any())).thenReturn(Map.of("access_token", "gho_x"));

    assertThat(service().exchange(form())).containsEntry("scope", "");
  }

  @ParameterizedTest(name = "{0}={1} -> {2}")
  @CsvSource({
    "grant_type,client_credentials,unsupported_grant_type",
    "grant_type,,unsupported_grant_type",
    "client_id,someone-else,invalid_client",
    "redirect_uri,https://evil.example/cb,invalid_request",
    "redirect_uri,,invalid_request",
    "code,,invalid_request",
    "code_verifier,too-short,invalid_request",
    "code_verifier,,invalid_request"
  })
  void invalidRequests_areRejectedWithoutCallingGitHub(
      String field, String value, String expectedError) {
    MultiValueMap<String, String> form = form();
    form.remove(field);
    if (value != null) {
      form.add(field, value);
    }

    assertThatThrownBy(() -> service().exchange(form))
        .isInstanceOfSatisfying(
            OAuthErrorException.class, e -> assertThat(e.error()).isEqualTo(expectedError));
    verifyNoInteractions(client);
  }

  @Test
  void verifierWithIllegalCharacters_isRejected() {
    MultiValueMap<String, String> form = form();
    form.set("code_verifier", "a".repeat(42) + "!");

    assertThatThrownBy(() -> service().exchange(form)).isInstanceOf(OAuthErrorException.class);
  }

  @Test
  void repeatedParameter_isInvalidRequest() {
    MultiValueMap<String, String> form = form();
    form.add("code", "second");

    assertThatThrownBy(() -> service().exchange(form))
        .isInstanceOfSatisfying(
            OAuthErrorException.class, e -> assertThat(e.error()).isEqualTo("invalid_request"));
  }

  @Test
  void codeRejectedByGitHub_isInvalidGrant() {
    when(client.exchangeCode(any(), any(), any()))
        .thenThrow(new GitHubOAuthException("bad_verification_code"));

    assertThatThrownBy(() -> service().exchange(form()))
        .isInstanceOfSatisfying(
            OAuthErrorException.class, e -> assertThat(e.error()).isEqualTo("invalid_grant"));
  }

  @Test
  void gitHubUnavailable_propagates() {
    when(client.exchangeCode(any(), any(), any()))
        .thenThrow(new GitHubUnavailableException("down", null));

    assertThatThrownBy(() -> service().exchange(form()))
        .isInstanceOf(GitHubUnavailableException.class);
  }
}
