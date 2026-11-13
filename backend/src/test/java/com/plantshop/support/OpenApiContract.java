package com.plantshop.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

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
            // MockMvc tests authenticate with post-processors instead of Authorization headers;
            // token handling is verified by the auth integration tests.
            .withLevelResolver(
                LevelResolver.create()
                    .withLevel("validation.request.security", ValidationReport.Level.IGNORE)
                    .build())
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
    for (String name : Collections.list(request.getHeaderNames())) {
      builder.withHeader(name, Collections.list(request.getHeaders(name)));
    }
    MultiValueMap<String, String> query =
        UriComponentsBuilder.newInstance().query(request.getQueryString()).build().getQueryParams();
    query.forEach(
        (name, values) ->
            builder.withQueryParam(
                name,
                values.stream().map(v -> URLDecoder.decode(v, StandardCharsets.UTF_8)).toList()));
    byte[] body = request.getContentAsByteArray();
    if (body != null && body.length > 0) {
      builder.withBody(new String(body, StandardCharsets.UTF_8));
    } else if (isForm(request)) {
      // MockMvc .param(...) on a form POST: parameters that are not in the query string are the
      // form body.
      StringBuilder form = new StringBuilder();
      request
          .getParameterMap()
          .forEach(
              (name, values) -> {
                if (!query.containsKey(name)) {
                  for (String value : values) {
                    form.append(form.isEmpty() ? "" : "&")
                        .append(URLEncoder.encode(name, StandardCharsets.UTF_8))
                        .append('=')
                        .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
                  }
                }
              });
      builder.withBody(form.toString());
    }
    return builder.build();
  }

  private static boolean isForm(MockHttpServletRequest request) {
    return request.getContentType() != null
        && MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(
            MediaType.parseMediaType(request.getContentType()));
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
