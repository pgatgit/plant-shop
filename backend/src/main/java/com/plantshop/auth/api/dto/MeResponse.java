package com.plantshop.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The signed-in shopper.
 *
 * @param ownerId stable owner id ({@code github:{id}} or {@code local:{username}})
 * @param displayName name to show in the header
 * @param avatarUrl avatar URL (GitHub only)
 * @param provider token source
 */
@Schema(name = "Me")
public record MeResponse(
    @Schema(example = "github:583231") String ownerId,
    @Schema(example = "Octo Cat") String displayName,
    @Schema(nullable = true, example = "https://avatars.githubusercontent.com/u/583231")
        String avatarUrl,
    @Schema(
            allowableValues = {"github", "local"},
            example = "github")
        String provider) {}
