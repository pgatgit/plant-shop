package com.plantshop.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request an id, logged as {@code requestId} and echoed in {@code X-Request-Id}
 * (NFR-OBS-02). A caller-supplied id is reused only if it is short and made of safe characters, so
 * it cannot inject anything into logs or headers.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

  private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String incoming = request.getHeader(Correlation.REQUEST_ID_HEADER);
    String requestId =
        incoming != null && SAFE_ID.matcher(incoming).matches()
            ? incoming
            : UUID.randomUUID().toString();
    response.setHeader(Correlation.REQUEST_ID_HEADER, requestId);
    MDC.put(Correlation.MDC_REQUEST_ID, requestId);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(Correlation.MDC_REQUEST_ID);
    }
  }
}
