package com.plantshop.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

/** WireMock stubs imitating GitHub's OAuth and "check a token" endpoints. */
public final class GitHubStubs {

  /** Client id configured for tests (src/test/resources/config/application.yml). */
  public static final String CLIENT_ID = "test-client-id";

  /** Client secret configured for tests. */
  public static final String CLIENT_SECRET = "test-client-secret-value";

  /** Path of GitHub's "check a token" endpoint for the test client. */
  public static final String CHECK_PATH = "/applications/" + CLIENT_ID + "/token";

  private GitHubStubs() {}

  /**
   * Matches a "check a token" call for the given token with correct basic auth.
   *
   * @param token the access token
   * @return the request pattern
   */
  public static MappingBuilder checkToken(String token) {
    return post(urlEqualTo(CHECK_PATH))
        .withBasicAuth(CLIENT_ID, CLIENT_SECRET)
        .withHeader("X-GitHub-Api-Version", equalTo("2022-11-28"))
        .withRequestBody(equalToJson("{\"access_token\":\"" + token + "\"}"));
  }

  /**
   * Stubs a successful token check.
   *
   * @param github the WireMock server
   * @param token the token
   * @param userId GitHub user id
   * @param clientId client id reported by GitHub (normally {@link #CLIENT_ID})
   */
  public static void validToken(
      WireMockExtension github, String token, long userId, String clientId) {
    github.stubFor(
        checkToken(token)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"id": 1, "token": "%s",
                         "app": {"client_id": "%s", "name": "Plant Shop"},
                         "user": {"id": %d, "login": "octocat", "name": "Octo Cat",
                                  "avatar_url": "https://avatars.githubusercontent.com/u/%d"}}
                        """
                            .formatted(token, clientId, userId, userId))));
  }

  /**
   * Stubs a token check answered with the given status (e.g. 404 for unknown tokens).
   *
   * @param github the WireMock server
   * @param token the token
   * @param status HTTP status GitHub returns
   */
  public static void tokenCheckStatus(WireMockExtension github, String token, int status) {
    github.stubFor(
        checkToken(token)
            .willReturn(aResponse().withStatus(status).withBody("{\"message\":\"x\"}")));
  }
}
