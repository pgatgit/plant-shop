package com.plantshop.cart.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * The shopper's cart. Every cart mutation returns the whole cart.
 *
 * @param items lines in the order they were added
 * @param itemCount sum of all quantities (the header badge)
 * @param subtotal sum of all line totals, two decimals
 * @param currency ISO 4217 currency code (always USD)
 */
@Schema(name = "Cart")
public record CartResponse(
    List<CartItemResponse> items,
    @Schema(example = "2", minimum = "0") int itemCount,
    @Schema(
            type = "string",
            format = "decimal",
            pattern = "^\\d{1,10}\\.\\d{2}$",
            example = "53.98")
        String subtotal,
    @Schema(example = "USD", allowableValues = "USD") String currency) {

  /** Defensive copy of the lines. */
  public CartResponse {
    items = List.copyOf(items);
  }
}
