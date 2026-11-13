package com.plantshop.auth.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Ticker;
import com.plantshop.auth.security.AuthProviderUnavailableException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;

@ExtendWith(MockitoExtension.class)
class GitHubTokenIntrospectorTest {

  private static final GitHubUser OCTOCAT =
      new GitHubUser(583231, "octocat", "Octo Cat", "https://avatars/1");

  /** Manually advanced cache clock. */
  static final class FakeTicker implements Ticker {
    private final AtomicLong nanos = new AtomicLong();

    @Override
    public long read() {
      return nanos.get();
    }

    void advance(Duration duration) {
      nanos.addAndGet(duration.toNanos());
    }
  }

  @Mock private GitHubClient client;
  private final FakeTicker ticker = new FakeTicker();
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

  private GitHubTokenIntrospector introspector() {
    GitHubProperties properties =
        new GitHubProperties(
            "id",
            "secret",
            null,
            null,
            null,
            Duration.ofMinutes(5),
            Duration.ofSeconds(30),
            null,
            null);
    return new GitHubTokenIntrospector(client, properties, ticker, meters);
  }

  @Test
  @DisplayName("[D3] a valid token becomes github:{id} with every shopper scope")
  void validToken_mapsToShopperPrincipal() {
    when(client.checkToken("gho_valid")).thenReturn(Optional.of(OCTOCAT));

    OAuth2AuthenticatedPrincipal principal = introspector().introspect("gho_valid");

    assertThat(principal.getName()).isEqualTo("github:583231");
    assertThat(principal.<String>getAttribute("login")).isEqualTo("octocat");
    assertThat(principal.<String>getAttribute("name")).isEqualTo("Octo Cat");
    assertThat(principal.<String>getAttribute("avatar_url")).isEqualTo("https://avatars/1");
    assertThat(principal.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder(
            "SCOPE_catalog.read", "SCOPE_cart.read", "SCOPE_cart.write", "ROLE_SHOPPER");
  }

  @Test
  void userWithoutNameOrAvatar_omitsThoseAttributes() {
    when(client.checkToken("t")).thenReturn(Optional.of(new GitHubUser(1, "x", null, null)));

    OAuth2AuthenticatedPrincipal principal = introspector().introspect("t");

    assertThat(principal.getAttributes()).containsOnlyKeys("login");
  }

  @Test
  @DisplayName("[NFR-REL-05] valid tokens are cached for the TTL, then re-checked")
  void validToken_isCachedUntilTtlExpires() {
    when(client.checkToken("gho_valid")).thenReturn(Optional.of(OCTOCAT));
    GitHubTokenIntrospector introspector = introspector();

    introspector.introspect("gho_valid");
    ticker.advance(Duration.ofMinutes(4));
    introspector.introspect("gho_valid");
    verify(client, times(1)).checkToken("gho_valid");

    ticker.advance(Duration.ofMinutes(2));
    introspector.introspect("gho_valid");
    verify(client, times(2)).checkToken("gho_valid");
    assertThat(
            meters
                .counter("plantshop.auth.token_check", "source", "cache", "outcome", "valid")
                .count())
        .isEqualTo(1.0);
  }

  @Test
  @DisplayName("[NFR-SEC-07] unknown or foreign tokens are rejected and negatively cached")
  void invalidToken_isRejectedAndNegativelyCached() {
    when(client.checkToken("gho_bad")).thenReturn(Optional.empty());
    GitHubTokenIntrospector introspector = introspector();

    assertThatThrownBy(() -> introspector.introspect("gho_bad"))
        .isInstanceOf(BadOpaqueTokenException.class);
    assertThatThrownBy(() -> introspector.introspect("gho_bad"))
        .isInstanceOf(BadOpaqueTokenException.class);
    verify(client, times(1)).checkToken("gho_bad");

    ticker.advance(Duration.ofSeconds(31));
    assertThatThrownBy(() -> introspector.introspect("gho_bad"))
        .isInstanceOf(BadOpaqueTokenException.class);
    verify(client, times(2)).checkToken("gho_bad");
  }

  @Test
  @DisplayName("[NFR-REL-05] GitHub outages surface as provider-unavailable and are not cached")
  void githubUnavailable_isNotCached() {
    when(client.checkToken("gho_x"))
        .thenThrow(new GitHubUnavailableException("down", null))
        .thenReturn(Optional.of(OCTOCAT));
    GitHubTokenIntrospector introspector = introspector();

    assertThatThrownBy(() -> introspector.introspect("gho_x"))
        .isInstanceOf(AuthProviderUnavailableException.class);
    assertThat(introspector.introspect("gho_x").getName()).isEqualTo("github:583231");
  }

  @Test
  void evict_forcesARecheck() {
    when(client.checkToken("gho_valid")).thenReturn(Optional.of(OCTOCAT));
    GitHubTokenIntrospector introspector = introspector();

    introspector.introspect("gho_valid");
    introspector.evict("gho_valid");
    introspector.introspect("gho_valid");

    verify(client, times(2)).checkToken("gho_valid");
  }
}
