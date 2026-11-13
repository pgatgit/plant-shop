package com.plantshop.support;

import com.plantshop.auth.config.AuthProperties;
import com.plantshop.auth.github.GitHubProperties;
import com.plantshop.auth.security.CorsConfig;
import com.plantshop.auth.security.CurrentUserResolver;
import com.plantshop.auth.security.ProblemAccessDeniedHandler;
import com.plantshop.auth.security.ProblemAuthenticationEntryPoint;
import com.plantshop.auth.security.ProblemResponseWriter;
import com.plantshop.auth.security.SecurityConfig;
import com.plantshop.auth.security.TokenAuthenticationManagerResolver;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/** The production security chains for {@code @WebMvcTest} slices. */
@TestConfiguration(proxyBeanMethods = false)
@EnableConfigurationProperties({AuthProperties.class, GitHubProperties.class})
@Import({
  SecurityConfig.class,
  CorsConfig.class,
  TokenAuthenticationManagerResolver.class,
  CurrentUserResolver.class,
  ProblemResponseWriter.class,
  ProblemAuthenticationEntryPoint.class,
  ProblemAccessDeniedHandler.class
})
public class WebMvcSecurityTestConfig {}
