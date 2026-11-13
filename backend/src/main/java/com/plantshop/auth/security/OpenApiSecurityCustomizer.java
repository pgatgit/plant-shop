package com.plantshop.auth.security;

import com.plantshop.auth.config.AuthMode;
import com.plantshop.auth.config.AuthProperties;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Adds the OAuth2 security scheme so Swagger UI's "Authorize" works (docs/plan/07-auth-design.md
 * §8): Local AS endpoints when mode contains {@code local}, otherwise GitHub with the backend's
 * token-exchange proxy as token URL.
 */
@Component
public class OpenApiSecurityCustomizer implements GlobalOpenApiCustomizer {

  /** Name of the security scheme in the OpenAPI document. */
  public static final String SCHEME = "oauth2";

  private final AuthProperties auth;
  private final String apiBaseUrl;

  /**
   * Creates the customizer.
   *
   * @param auth auth settings
   * @param apiBaseUrl the API base URL advertised in the document
   */
  public OpenApiSecurityCustomizer(
      AuthProperties auth,
      @Value("${app.openapi.server-url:http://127.0.0.1:8080}") String apiBaseUrl) {
    this.auth = auth;
    this.apiBaseUrl = apiBaseUrl;
  }

  @Override
  public void customise(io.swagger.v3.oas.models.OpenAPI openApi) {
    OAuthFlow flow;
    if (auth.has(AuthMode.LOCAL)) {
      String issuer = auth.local().issuer();
      flow =
          new OAuthFlow()
              .authorizationUrl(issuer + "/oauth2/authorize")
              .tokenUrl(issuer + "/oauth2/token")
              .scopes(
                  new Scopes()
                      .addString("openid", "OpenID Connect")
                      .addString(com.plantshop.auth.security.Scopes.CATALOG_READ, "Read catalog")
                      .addString(com.plantshop.auth.security.Scopes.CART_READ, "Read own cart")
                      .addString(com.plantshop.auth.security.Scopes.CART_WRITE, "Modify own cart"));
    } else {
      flow =
          new OAuthFlow()
              .authorizationUrl("https://github.com/login/oauth/authorize")
              .tokenUrl(apiBaseUrl + "/api/v1/auth/token")
              .scopes(new Scopes().addString("read:user", "Read the GitHub profile"));
    }
    if (openApi.getComponents() == null) {
      openApi.setComponents(new Components());
    }
    openApi
        .getComponents()
        .addSecuritySchemes(
            SCHEME,
            new SecurityScheme()
                .type(SecurityScheme.Type.OAUTH2)
                .description("Authorization Code with PKCE. Local AS (dev/e2e) or GitHub (prod).")
                .flows(new OAuthFlows().authorizationCode(flow)));
    openApi.setSecurity(List.of(new SecurityRequirement().addList(SCHEME)));
  }
}
