package com.plantshop.auth.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Documentation-only description of the form-encoded token request (RFC 6749 §4.1.3 + RFC 7636).
 *
 * @param grantType must be {@code authorization_code}
 * @param code the authorization code returned by GitHub
 * @param redirectUri the redirect URI used in the authorization request
 * @param clientId the GitHub OAuth App client id
 * @param codeVerifier the PKCE code verifier
 */
@Schema(name = "TokenRequest", description = "application/x-www-form-urlencoded body")
public record TokenRequestForm(
    @JsonProperty("grant_type") @Schema(allowableValues = "authorization_code") String grantType,
    @JsonProperty("code") String code,
    @JsonProperty("redirect_uri") @Schema(example = "http://127.0.0.1:3000/auth/callback")
        String redirectUri,
    @JsonProperty("client_id") String clientId,
    @JsonProperty("code_verifier") @Schema(minLength = 43, maxLength = 128) String codeVerifier) {}
