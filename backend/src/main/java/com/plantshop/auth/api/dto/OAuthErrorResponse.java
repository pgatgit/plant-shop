package com.plantshop.auth.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * RFC 6749 §5.2 error body returned by the token endpoint.
 *
 * @param error the error code
 * @param errorDescription human-readable description
 */
@Schema(name = "OAuthError")
public record OAuthErrorResponse(
    @JsonProperty("error")
        @Schema(
            allowableValues = {
              "invalid_request",
              "invalid_client",
              "invalid_grant",
              "unsupported_grant_type",
              "temporarily_unavailable"
            })
        String error,
    @JsonProperty("error_description") String errorDescription) {}
