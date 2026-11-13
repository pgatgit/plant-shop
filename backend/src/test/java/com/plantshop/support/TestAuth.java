package com.plantshop.support;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Authentication helpers for MockMvc tests. M1 uses mock principals with scope authorities; M2
 * switches these to real JWT / opaque-token post-processors.
 */
public final class TestAuth {

  private TestAuth() {}

  /**
   * A shopper holding every scope.
   *
   * @return request post-processor
   */
  public static RequestPostProcessor shopper() {
    return withScopes("shopper", "catalog.read", "cart.read", "cart.write");
  }

  /**
   * A principal with exactly the given scopes.
   *
   * @param username the principal name
   * @param scopes OAuth2 scopes (without the {@code SCOPE_} prefix)
   * @return request post-processor
   */
  public static RequestPostProcessor withScopes(String username, String... scopes) {
    SimpleGrantedAuthority[] authorities = new SimpleGrantedAuthority[scopes.length];
    for (int i = 0; i < scopes.length; i++) {
      authorities[i] = new SimpleGrantedAuthority("SCOPE_" + scopes[i]);
    }
    return SecurityMockMvcRequestPostProcessors.user(username).authorities(authorities);
  }
}
