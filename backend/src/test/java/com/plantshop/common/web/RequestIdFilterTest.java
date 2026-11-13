package com.plantshop.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("[NFR-OBS-02] RequestIdFilter")
class RequestIdFilterTest {

  private final RequestIdFilter filter = new RequestIdFilter();
  private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cart");
  private final MockHttpServletResponse response = new MockHttpServletResponse();
  private final AtomicReference<String> mdcDuringChain = new AtomicReference<>();
  private final FilterChain chain =
      (req, res) -> mdcDuringChain.set(MDC.get(Correlation.MDC_REQUEST_ID));

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void withoutHeader_generatesIdAndExposesItInMdcAndResponse() throws Exception {
    filter.doFilter(request, response, chain);

    String id = response.getHeader(Correlation.REQUEST_ID_HEADER);
    assertThat(id).matches("[0-9a-f-]{36}");
    assertThat(mdcDuringChain.get()).isEqualTo(id);
    assertThat(MDC.get(Correlation.MDC_REQUEST_ID)).as("MDC is cleaned up").isNull();
  }

  @Test
  void validIncomingId_isEchoed() throws Exception {
    request.addHeader(Correlation.REQUEST_ID_HEADER, "abc-123_X.y");

    filter.doFilter(request, response, chain);

    assertThat(response.getHeader(Correlation.REQUEST_ID_HEADER)).isEqualTo("abc-123_X.y");
    assertThat(mdcDuringChain.get()).isEqualTo("abc-123_X.y");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "has space", "line\nbreak", "<script>", "ünïcode"})
  void unsafeIncomingId_isReplaced(String incoming) throws Exception {
    request.addHeader(Correlation.REQUEST_ID_HEADER, incoming);

    filter.doFilter(request, response, chain);

    assertThat(response.getHeader(Correlation.REQUEST_ID_HEADER))
        .isNotEqualTo(incoming)
        .matches("[0-9a-f-]{36}");
  }

  @Test
  void overlongIncomingId_isReplaced() throws Exception {
    request.addHeader(Correlation.REQUEST_ID_HEADER, "a".repeat(65));

    filter.doFilter(request, response, chain);

    assertThat(response.getHeader(Correlation.REQUEST_ID_HEADER)).matches("[0-9a-f-]{36}");
  }

  @Test
  void mdcIsCleanedUpEvenWhenTheChainFails() {
    FilterChain failing =
        (req, res) -> {
          throw new IllegalStateException("boom");
        };

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> filter.doFilter(request, response, failing))
        .isInstanceOf(IllegalStateException.class);
    assertThat(MDC.get(Correlation.MDC_REQUEST_ID)).isNull();
  }
}
