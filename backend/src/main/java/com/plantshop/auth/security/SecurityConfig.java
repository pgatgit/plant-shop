package com.plantshop.auth.security;

import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * HTTP security (docs/plan/04-backend-design.md §7, docs/plan/07-auth-design.md).
 *
 * <p>M1 state: every API endpoint requires an authenticated principal with the right scope, but no
 * token validation is wired yet, so the running API answers 401 to everyone. M2 adds the OAuth2
 * resource server (GitHub token check and the dev/e2e Local AS) to the API chain.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

  /** Scope required to read the catalog. */
  public static final String SCOPE_CATALOG_READ = "SCOPE_catalog.read";

  /**
   * Stateless bearer-token chain for {@code /api/**}.
   *
   * @param http the security builder
   * @param entryPoint 401 problem writer
   * @param accessDeniedHandler 403 problem writer
   * @return the chain
   * @throws Exception if the configuration is invalid
   */
  @Bean
  @Order(2)
  public SecurityFilterChain apiSecurityFilterChain(
      HttpSecurity http,
      ProblemAuthenticationEntryPoint entryPoint,
      ProblemAccessDeniedHandler accessDeniedHandler)
      throws Exception {
    http.securityMatcher("/api/**")
        .csrf(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.GET, "/api/v1/products")
                    .hasAuthority(SCOPE_CATALOG_READ)
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler));
    return http.build();
  }

  /**
   * Actuator endpoints on the management port (8081, not published outside the container network):
   * health probes and Prometheus scraping need no authentication.
   *
   * @param http the security builder
   * @return the chain
   * @throws Exception if the configuration is invalid
   */
  @Bean
  @Order(3)
  public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher(EndpointRequest.toAnyEndpoint())
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    return http.build();
  }

  /**
   * OpenAPI document and Swagger UI. springdoc only maps these paths when {@code
   * app.swagger.enabled} is true; otherwise they fall through to 404.
   *
   * @param http the security builder
   * @return the chain
   * @throws Exception if the configuration is invalid
   */
  @Bean
  @Order(4)
  public SecurityFilterChain docsSecurityFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher(
            "/v3/api-docs",
            "/v3/api-docs.yaml",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**")
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    return http.build();
  }

  /**
   * Everything else is denied.
   *
   * @param http the security builder
   * @param entryPoint 401 problem writer
   * @param accessDeniedHandler 403 problem writer
   * @return the chain
   * @throws Exception if the configuration is invalid
   */
  @Bean
  @Order(100)
  public SecurityFilterChain denyAllSecurityFilterChain(
      HttpSecurity http,
      ProblemAuthenticationEntryPoint entryPoint,
      ProblemAccessDeniedHandler accessDeniedHandler)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth.anyRequest().denyAll())
        .exceptionHandling(
            e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler));
    return http.build();
  }
}
