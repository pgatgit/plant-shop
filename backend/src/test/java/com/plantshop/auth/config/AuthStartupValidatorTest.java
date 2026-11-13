package com.plantshop.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.plantshop.auth.github.GitHubProperties;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AuthStartupValidatorTest {

  private static final GitHubProperties GITHUB =
      new GitHubProperties(
          "id",
          "secret",
          List.of("http://127.0.0.1:3000/auth/callback"),
          null,
          null,
          null,
          null,
          null,
          null);
  private static final GitHubProperties NO_GITHUB =
      new GitHubProperties(null, null, null, null, null, null, null, null, null);

  private static AuthProperties auth(Set<AuthMode> mode, String password) {
    return new AuthProperties(mode, new AuthProperties.Local(null, null, null, password, null));
  }

  private static AuthStartupValidator validator(
      AuthProperties auth, GitHubProperties github, String... profiles) {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles(profiles);
    return new AuthStartupValidator(auth, github, env);
  }

  @Test
  @DisplayName("[NFR-SEC-08] local users are rejected in the prod profile")
  void localModeInProd_isRejected() {
    AuthStartupValidator v =
        validator(auth(Set.of(AuthMode.GITHUB, AuthMode.LOCAL), "pw"), GITHUB, "prod");

    assertThatIllegalStateException()
        .isThrownBy(v::afterPropertiesSet)
        .withMessageContaining("must not contain 'local' in the prod profile");
  }

  @Test
  void localModeWithoutDevOrE2eProfile_isRejected() {
    assertThat(validator(auth(Set.of(AuthMode.LOCAL), "pw"), NO_GITHUB).problems())
        .containsExactly("app.auth.mode 'local' requires the dev or e2e profile");
  }

  @Test
  void localModeWithoutPassword_isRejected() {
    assertThat(validator(auth(Set.of(AuthMode.LOCAL), " "), NO_GITHUB, "dev").problems())
        .containsExactly("app.auth.local.user-password must be set when mode contains 'local'");
  }

  @Test
  @DisplayName("GitHub mode requires client id, secret and redirect URIs")
  void githubModeWithoutCredentials_listsEveryProblem() {
    GitHubProperties empty =
        new GitHubProperties("", null, List.of(), null, null, null, null, null, null);

    assertThat(validator(auth(Set.of(AuthMode.GITHUB), null), empty, "prod").problems())
        .hasSize(3)
        .anyMatch(p -> p.contains("GITHUB_CLIENT_ID"))
        .anyMatch(p -> p.contains("GITHUB_CLIENT_SECRET"))
        .anyMatch(p -> p.contains("allowed-redirect-uris"));
  }

  @Test
  void validConfigurations_pass() {
    validator(auth(Set.of(AuthMode.GITHUB), null), GITHUB, "prod").afterPropertiesSet();
    validator(auth(Set.of(AuthMode.LOCAL), "pw"), NO_GITHUB, "e2e").afterPropertiesSet();
    validator(auth(Set.of(AuthMode.GITHUB, AuthMode.LOCAL), "pw"), GITHUB, "dev")
        .afterPropertiesSet();
  }

  @Test
  void defaults_areGithubModeAndLocalUrls() {
    AuthProperties defaults = new AuthProperties(null, null);

    assertThat(defaults.mode()).containsExactly(AuthMode.GITHUB);
    assertThat(defaults.local().issuer()).isEqualTo("http://127.0.0.1:8080");
    assertThat(defaults.local().users()).isEmpty();
    assertThat(NO_GITHUB.tokenCacheTtl()).hasMinutes(5);
    assertThat(NO_GITHUB.toString()).doesNotContain("secret").contains("clientSecret=***");
    assertThat(GITHUB.toString()).doesNotContain("=secret");
  }
}
