package com.plantshop.catalog.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A product category.
 *
 * @param code stable identifier
 * @param name display name
 */
@Schema(name = "Category")
public record CategoryResponse(
    @Schema(example = "FERNS") String code, @Schema(example = "Ferns") String name) {}
