package com.plantshop.auth.api;

import com.plantshop.auth.api.dto.MeResponse;
import com.plantshop.auth.security.CurrentUser;
import com.plantshop.auth.security.CurrentUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Account", description = "The signed-in shopper")
public class MeController {

  private final CurrentUserResolver currentUsers;

  public MeController(CurrentUserResolver currentUsers) {
    this.currentUsers = currentUsers;
  }

  @GetMapping
  @Operation(
      operationId = "getMe",
      summary = "Get the signed-in shopper",
      description = "Requires any valid access token.")
  @ApiResponse(responseCode = "200", description = "The signed-in shopper")
  @ApiResponse(
      responseCode = "401",
      description = "Missing or invalid access token",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "503",
      description = "The identity provider could not validate the token",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  public MeResponse getMe(Authentication authentication) {
    CurrentUser user = currentUsers.resolve(authentication);
    return new MeResponse(user.ownerId(), user.displayName(), user.avatarUrl(), user.provider());
  }
}
