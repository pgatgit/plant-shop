package com.plantshop.auth.security;

import com.plantshop.auth.github.TokenExchangeRateLimitFilter;
import com.plantshop.auth.github.TokenExchangeRateLimiter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.CorsFilter;

/**
 * HTTP security (docs/plan/04-backend-design.md §7, docs/plan/07-auth-design.md). Chain order:
 * Local AS endpoints (order 1, dev/e2e only) → API (2) → actuator (3) → API docs (4) → Local AS
 * login page (5, dev/e2e only) → deny everything else (100).
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

  /**
   * Stateless bearer-token chain for {@code /api/**}.
   *
   * @param http the security builder
   * @param tokenResolver picks the token validator (Local AS JWT or GitHub)
   * @param entryPoint 401/503 problem writer
   * @param accessDeniedHandler 403 problem writer
   * @param rateLimiter token-exchange rate limiter (GitHub mode only)
   * @param problems problem writer for 429 responses
   * @param currentUsers resolves the owner logged as {@code ownerHash}
   * @return the chain
   * @throws Exception if the configuration is invalid
   */
  @Bean
  @Order(2)
  public SecurityFilterChain apiSecurityFilterChain(
      HttpSecurity http,
      TokenAuthenticationManagerResolver tokenResolver,
      ProblemAuthenticationEntryPoint entryPoint,
      ProblemAccessDeniedHandler accessDeniedHandler,
      ObjectProvider<TokenExchangeRateLimiter> rateLimiter,
      ProblemResponseWriter problems,
      CurrentUserResolver currentUsers)
      throws Exception {
    http.securityMatcher("/api/**")
        .cors(Customizer.withDefaults())
        .csrf(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.POST, TokenExchangeRateLimitFilter.PATH)
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/products")
                    .hasAuthority(Scopes.authority(Scopes.CATALOG_READ))
                    .requestMatchers(HttpMethod.GET, "/api/v1/cart")
                    .hasAuthority(Scopes.authority(Scopes.CART_READ))
                    .requestMatchers("/api/v1/cart/**")
                    .hasAuthority(Scopes.authority(Scopes.CART_WRITE))
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            rs ->
                rs.authenticationManagerResolver(tokenResolver)
                    .authenticationEntryPoint(entryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .exceptionHandling(
            e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler));
    http.addFilterAfter(new OwnerMdcFilter(currentUsers), BearerTokenAuthenticationFilter.class);
    TokenExchangeRateLimiter limiter = rateLimiter.getIfAvailable();
    if (limiter != null) {
      http.addFilterAfter(new TokenExchangeRateLimitFilter(limiter, problems), CorsFilter.class);
    }
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
