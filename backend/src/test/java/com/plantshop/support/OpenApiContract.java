package com.plantshop.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * Validates MockMvc request/response pairs against the committed contract {@code
 * docs/api/openapi.yaml} (docs/plan/09-testing-strategy.md §3.5). A small adapter is used because
 * the library's own MockMvc module targets {@code javax.servlet}.
 */
public final class OpenApiContract {

  /** Location of the committed contract, relative to the backend module. */
  public static final Path SPEC = Path.of("..", "docs", "api", "openapi.yaml");

  /** Lazily built so that merely referencing {@link #SPEC} never requires the file to exist. */
  private static final class ValidatorHolder {
    private static final OpenApiInteractionValidator VALIDATOR =
        OpenApiInteractionValidator.createForSpecificationUrl(
                SPEC.toAbsolutePath().normalize().toUri().toString())
            .build();
  }

  private OpenApiContract() {}

  /**
   * Matcher asserting that the exchange conforms to the contract.
   *
   * @return a MockMvc result matcher
   */
  public static ResultMatcher conformsToContract() {
    return OpenApiContract::assertConforms;
  }

  private static void assertConforms(MvcResult result) throws Exception {
    ValidationReport report =
        ValidatorHolder.VALIDATOR.validate(toRequest(result), toResponse(result));
    assertThat(report.getMessages())
        .as("OpenAPI contract violations for %s", result.getRequest().getRequestURI())
        .isEmpty();
  }

  private static SimpleRequest toRequest(MvcResult result) {
    MockHttpServletRequest request = result.getRequest();
    SimpleRequest.Builder builder =
        new SimpleRequest.Builder(
            Request.Method.valueOf(request.getMethod()), request.getRequestURI());
    request.getParameterMap().forEach(builder::withQueryParam);
    for (String name : Collections.list(request.getHeaderNames())) {
      builder.withHeader(name, Collections.list(request.getHeaders(name)));
    }
    byte[] body = request.getContentAsByteArray();
    if (body != null && body.length > 0) {
      builder.withBody(new String(body, StandardCharsets.UTF_8));
    }
    return builder.build();
  }

  private static SimpleResponse toResponse(MvcResult result) throws Exception {
    MockHttpServletResponse response = result.getResponse();
    SimpleResponse.Builder builder = SimpleResponse.Builder.status(response.getStatus());
    if (response.getContentType() != null) {
      builder.withContentType(response.getContentType());
    }
    String body = response.getContentAsString(StandardCharsets.UTF_8);
    if (!body.isEmpty()) {
      builder.withBody(body);
    }
    return builder.build();
  }
}
