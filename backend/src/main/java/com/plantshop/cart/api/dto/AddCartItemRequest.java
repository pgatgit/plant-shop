package com.plantshop.cart.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Adds a product to the cart, or increases its line.
 *
 * @param productId the product to add
 * @param quantity how many to add; defaults to 1
 */
@Schema(name = "AddCartItemRequest")
public record AddCartItemRequest(
    @Schema(example = "11", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Positive
        Long productId,
    @Schema(example = "1", minimum = "1", maximum = "10", defaultValue = "1") @Min(1) @Max(10)
        Integer quantity) {

  /**
   * Returns the quantity to add, applying the default.
   *
   * @return the quantity, 1 if omitted
   */
  public int quantityOrDefault() {
    return quantity == null ? 1 : quantity;
  }
}
