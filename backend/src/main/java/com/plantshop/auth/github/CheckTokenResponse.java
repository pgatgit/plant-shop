package com.plantshop.auth.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The parts of GitHub's "check a token" response the backend uses.
 *
 * @param user the token's user
 * @param app the OAuth App the token was issued to
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record CheckTokenResponse(User user, App app) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  record User(long id, String login, String name, @JsonProperty("avatar_url") String avatarUrl) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record App(@JsonProperty("client_id") String clientId) {}
}
