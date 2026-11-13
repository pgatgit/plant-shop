package com.plantshop.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantshop.common.web.Correlation;
import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@DisplayName("[NFR-OBS-02] OwnerMdcFilter")
class OwnerMdcFilterTest {

  private final OwnerMdcFilter filter = new OwnerMdcFilter(new CurrentUserResolver());
  private final AtomicReference<String> seen = new AtomicReference<>("unset");
  private final FilterChain chain = (req, res) -> seen.set(MDC.get(Correlation.MDC_OWNER));

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
    MDC.clear();
  }

  @Test
  void authenticatedRequest_logsOwnerHashNotOwnerId() throws Exception {
    Jwt jwt =
        Jwt.withTokenValue("t")
            .header("alg", "none")
            .subject("shopper")
            .claim("scope", "x")
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, java.util.List.of()));

    filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

    assertThat(seen.get())
        .isEqualTo(Correlation.ownerHash("local:shopper"))
        .doesNotContain("shopper");
    assertThat(MDC.get(Correlation.MDC_OWNER)).isNull();
  }

  @Test
  void anonymousRequest_hasNoOwner() throws Exception {
    filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

    assertThat(seen.get()).isNull();
  }

  @Test
  void unsupportedAuthentication_isIgnored() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("someone", "pw", "ROLE_X"));

    filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

    assertThat(seen.get()).isNull();
  }
}
