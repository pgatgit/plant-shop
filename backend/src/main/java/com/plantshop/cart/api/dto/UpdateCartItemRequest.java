package com.plantshop.cart.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Sets the quantity of a line. Use DELETE to remove a line; 0 is rejected.
 *
 * @param quantity the new quantity
 */
@Schema(name = "UpdateCartItemRequest")
public record UpdateCartItemRequest(
    @Schema(
            example = "3",
            minimum = "1",
            maximum = "10",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Min(1)
        @Max(10)
        Integer quantity) {}
