package com.plantshop.auth.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Documentation-only description of a successful token response (RFC 6749 §5.1).
 *
 * @param accessToken the GitHub access token
 * @param tokenType always {@code bearer}
 * @param scope granted GitHub scopes
 */
@Schema(name = "TokenResponse")
public record TokenResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("token_type") @Schema(allowableValues = "bearer") String tokenType,
    @JsonProperty("scope") @Schema(example = "read:user") String scope) {}
