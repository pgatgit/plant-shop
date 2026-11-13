package com.plantshop.auth.api;

import com.plantshop.auth.api.dto.OAuthErrorResponse;
import com.plantshop.auth.api.dto.TokenRequestForm;
import com.plantshop.auth.api.dto.TokenResponse;
import com.plantshop.auth.config.AuthMode;
import com.plantshop.auth.config.ConditionalOnAuthMode;
import com.plantshop.auth.github.GitHubUnavailableException;
import com.plantshop.auth.github.OAuthErrorException;
import com.plantshop.auth.github.TokenExchangeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "GitHub sign-in support")
@ConditionalOnAuthMode(AuthMode.GITHUB)
public class TokenExchangeController {

  private static final Logger LOG = LoggerFactory.getLogger(TokenExchangeController.class);

  private final TokenExchangeService tokenExchange;

  public TokenExchangeController(TokenExchangeService tokenExchange) {
    this.tokenExchange = tokenExchange;
  }

  @PostMapping(path = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  @SecurityRequirements
  @Operation(
      operationId = "exchangeToken",
      summary = "Exchange a GitHub authorization code for an access token",
      description =
          "OAuth 2.0 token endpoint for the SPA and Swagger UI (Authorization Code + PKCE). The"
              + " backend adds the GitHub client secret and forwards the request to GitHub."
              + " Errors use RFC 6749 §5.2 JSON. Rate limited per client IP.",
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              required = true,
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                      schema = @Schema(implementation = TokenRequestForm.class))))
  @ApiResponse(
      responseCode = "200",
      description = "The GitHub access token",
      content = @Content(schema = @Schema(implementation = TokenResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "Invalid request, client, grant or grant type",
      content = @Content(schema = @Schema(implementation = OAuthErrorResponse.class)))
  @ApiResponse(
      responseCode = "429",
      description = "Too many token requests from this client",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "502",
      description = "GitHub could not be reached",
      content = @Content(schema = @Schema(implementation = OAuthErrorResponse.class)))
  public ResponseEntity<Map<String, Object>> exchangeToken(
      @RequestParam MultiValueMap<String, String> form) {
    return noStore(HttpStatus.OK).body(tokenExchange.exchange(form));
  }

  @ExceptionHandler(OAuthErrorException.class)
  ResponseEntity<Map<String, Object>> oauthError(OAuthErrorException e) {
    LOG.debug("Token exchange rejected: {}", e.error());
    return noStore(HttpStatus.BAD_REQUEST)
        .body(Map.of("error", e.error(), "error_description", e.getMessage()));
  }

  @ExceptionHandler(GitHubUnavailableException.class)
  ResponseEntity<Map<String, Object>> githubUnavailable(GitHubUnavailableException e) {
    LOG.warn("Token exchange failed: {}", e.getMessage());
    return noStore(HttpStatus.BAD_GATEWAY)
        .body(
            Map.of(
                "error",
                "temporarily_unavailable",
                "error_description",
                "GitHub could not be reached. Please try again."));
  }

  /** RFC 6749 §5.1: token responses must not be cached. */
  private static ResponseEntity.BodyBuilder noStore(HttpStatus status) {
    return ResponseEntity.status(status)
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.PRAGMA, "no-cache")
        .contentType(MediaType.APPLICATION_JSON);
  }
}
