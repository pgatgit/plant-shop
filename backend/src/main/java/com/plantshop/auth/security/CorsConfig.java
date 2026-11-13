package com.plantshop.auth.security;

import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Cross-origin access for the SPA on its own origin (D14, ADR-007): exact origins from {@code
 * APP_CORS_ALLOWED_ORIGINS}, bearer tokens only (no credentials), applied to the API and the token
 * endpoints the SPA calls with XHR.
 */
@Configuration(proxyBeanMethods = false)
public class CorsConfig {

  /**
   * CORS rules used by every security chain that enables {@code cors()}.
   *
   * @param allowedOrigins exact origins, e.g. {@code http://127.0.0.1:3000}
   * @return the configuration source
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource(
      @Value("${app.cors.allowed-origins:}") List<String> allowedOrigins) {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(allowedOrigins.stream().filter(o -> !o.isBlank()).toList());
    config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Request-Id"));
    config.setExposedHeaders(List.of("X-Request-Id", "Retry-After", "WWW-Authenticate"));
    config.setAllowCredentials(false);
    config.setMaxAge(Duration.ofHours(1));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    source.registerCorsConfiguration("/oauth2/token", config);
    source.registerCorsConfiguration("/oauth2/jwks", config);
    return source;
  }
}
