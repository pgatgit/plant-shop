package com.plantshop.common.config;

import com.plantshop.common.error.ProblemCode;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document metadata and schema post-processing. The generated document is committed as
 * {@code docs/api/openapi.yaml} and guarded by {@code OpenApiSnapshotIT}
 * (docs/plan/11-documentation-plan.md §4).
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

  /** Component name of the Problem Details schema (as referenced by springdoc). */
  static final String PROBLEM_SCHEMA = "ProblemDetail";

  /** Request body schemas keep their annotation-derived required properties. */
  static final String REQUEST_SUFFIX = "Request";

  /** Component name of a single validation error. */
  static final String FIELD_ERROR_SCHEMA = "FieldError";

  /**
   * Builds the OpenAPI metadata.
   *
   * @param serverUrl the API base URL advertised in the document
   * @return the OpenAPI model
   */
  @Bean
  public OpenAPI plantShopOpenApi(
      @Value("${app.openapi.server-url:http://127.0.0.1:8080}") String serverUrl) {
    return new OpenAPI()
        .info(
            new Info()
                .title("Plant Shop API")
                .version("v1")
                .description(
                    "Catalog, name search and cart for the Plant Shop. Errors use RFC 9457"
                        + " Problem Details with a stable `code` property.")
                .license(new License().name("Proprietary")))
        .servers(List.of(new Server().url(serverUrl).description("Local")))
        .tags(
            List.of(new Tag().name("Catalog").description("Browse and search the plant catalog")));
  }

  /**
   * Makes the contract strict and accurate.
   *
   * <ul>
   *   <li>Every response property that is not nullable is marked required, so clients (and
   *       generated TypeScript types) can rely on it. Request bodies ({@code *Request} schemas)
   *       keep the required list derived from their validation annotations, so optional fields with
   *       defaults stay optional.
   *   <li>The {@code ProblemDetail} schema is replaced by the actual wire format: Jackson flattens
   *       extension properties, and every problem carries a {@code code}.
   * </ul>
   *
   * @return the customizer
   */
  @Bean
  @SuppressWarnings({"rawtypes", "unchecked"}) // swagger-core exposes raw Schema maps
  public OpenApiCustomizer contractCustomizer() {
    return openApi -> {
      Components components = openApi.getComponents();
      if (components == null || components.getSchemas() == null) {
        return;
      }
      components
          .getSchemas()
          .forEach(
              (name, schema) -> {
                Map<String, Schema> properties = schema.getProperties();
                if (properties != null && !name.endsWith(REQUEST_SUFFIX)) {
                  schema.setRequired(
                      properties.entrySet().stream()
                          .filter(e -> !Boolean.TRUE.equals(e.getValue().getNullable()))
                          .map(Map.Entry::getKey)
                          .sorted()
                          .toList());
                }
              });
      components.addSchemas(FIELD_ERROR_SCHEMA, fieldErrorSchema());
      components.addSchemas(PROBLEM_SCHEMA, problemSchema());
    };
  }

  @SuppressWarnings("unchecked") // swagger-core builder methods return raw Schema
  private static Schema<?> fieldErrorSchema() {
    return new ObjectSchema()
        .description("One rejected request value (validation-failed problems)")
        .addProperty("field", new StringSchema().example("size"))
        .addProperty("message", new StringSchema().example("must be less than or equal to 48"))
        .addProperty(
            "rejectedValue",
            new Schema<>().nullable(true).description("The rejected value, any JSON type"))
        .required(List.of("field", "message"));
  }

  @SuppressWarnings("unchecked") // swagger-core builder methods return raw Schema
  private static Schema<?> problemSchema() {
    List<String> codes = Arrays.stream(ProblemCode.values()).map(ProblemCode::slug).toList();
    return new ObjectSchema()
        .description(
            "RFC 9457 Problem Details. Extension members depend on `code`, e.g. `errors` for"
                + " validation-failed, `minLength`/`maxLength` for invalid-search-term,"
                + " `limit`/`requested`/`productId` for quantity-limit-exceeded. `traceId`"
                + " identifies the request in the server logs.")
        .addProperty(
            "type",
            new StringSchema()
                .format("uri")
                .example(ProblemCode.TYPE_BASE + ProblemCode.VALIDATION_FAILED.slug()))
        .addProperty("title", new StringSchema().example("Validation failed"))
        .addProperty("status", new IntegerSchema().example(400))
        .addProperty(
            "detail", new StringSchema().example("One or more request values are invalid."))
        .addProperty("instance", new StringSchema().format("uri").example("/api/v1/products"))
        .addProperty("code", new StringSchema()._enum(codes).example("validation-failed"))
        .addProperty(
            "errors",
            new ArraySchema()
                .items(new Schema<>().$ref("#/components/schemas/" + FIELD_ERROR_SCHEMA)))
        .addProperty(
            "limit", new IntegerSchema().description("Highest allowed line quantity").example(3))
        .addProperty("requested", new IntegerSchema().description("Rejected quantity").example(4))
        .addProperty(
            "productId", new IntegerSchema().format("int64").description("Product concerned"))
        .addProperty(
            "traceId",
            new StringSchema()
                .description("Trace id of the request, for support")
                .example("6f1c2b9a0d4e8f7a6f1c2b9a0d4e8f7a"))
        .additionalProperties(true)
        .required(List.of("code", "status", "title", "type"));
  }
}
