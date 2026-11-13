package com.plantshop.auth.config;

import com.plantshop.auth.github.GitHubProperties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Fails start-up on unsafe or incomplete auth configuration (NFR-SEC-08, threat T7): local users
 * can never be enabled in {@code prod}, the Local AS only exists in {@code dev}/{@code e2e}, and
 * GitHub mode needs its client credentials.
 */
@Component
public class AuthStartupValidator implements InitializingBean {

  private final AuthProperties auth;
  private final GitHubProperties github;
  private final Environment environment;

  /**
   * Creates the validator.
   *
   * @param auth auth settings
   * @param github GitHub settings
   * @param environment active profiles
   */
  public AuthStartupValidator(
      AuthProperties auth, GitHubProperties github, Environment environment) {
    this.auth = auth;
    this.github = github;
    this.environment = environment;
  }

  @Override
  public void afterPropertiesSet() {
    List<String> problems = problems();
    if (!problems.isEmpty()) {
      throw new IllegalStateException(
          "Invalid authentication configuration: " + String.join("; ", problems));
    }
  }

  /**
   * Lists every configuration problem (empty when the configuration is valid).
   *
   * @return human-readable problems
   */
  public List<String> problems() {
    List<String> problems = new ArrayList<>();
    List<String> profiles = Arrays.asList(environment.getActiveProfiles());
    boolean localProfile = profiles.contains("dev") || profiles.contains("e2e");
    if (auth.has(AuthMode.LOCAL) && profiles.contains("prod")) {
      problems.add("app.auth.mode must not contain 'local' in the prod profile");
    }
    if (auth.has(AuthMode.LOCAL) && !localProfile) {
      problems.add("app.auth.mode 'local' requires the dev or e2e profile");
    }
    if (auth.has(AuthMode.LOCAL) && !StringUtils.hasText(auth.local().userPassword())) {
      problems.add("app.auth.local.user-password must be set when mode contains 'local'");
    }
    if (auth.has(AuthMode.GITHUB)) {
      if (!StringUtils.hasText(github.clientId())) {
        problems.add("app.github.client-id (GITHUB_CLIENT_ID) must be set in GitHub mode");
      }
      if (!StringUtils.hasText(github.clientSecret())) {
        problems.add("app.github.client-secret (GITHUB_CLIENT_SECRET) must be set in GitHub mode");
      }
      if (github.allowedRedirectUris().isEmpty()) {
        problems.add("app.github.allowed-redirect-uris must list at least one redirect URI");
      }
    }
    return problems;
  }
}
