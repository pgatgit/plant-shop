package com.plantshop.cart.api.dto;

import com.plantshop.cart.domain.StockStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One line of the cart.
 *
 * @param productId product id
 * @param name product name
 * @param imagePath product illustration, relative to the SPA origin
 * @param unitPrice current unit price, two decimals
 * @param quantity line quantity
 * @param lineTotal unit price × quantity, two decimals
 * @param stock units currently in stock
 * @param maxQuantity highest quantity this line may be set to: min(stock, 10)
 * @param stockStatus line quantity compared with current stock
 */
@Schema(name = "CartItem")
public record CartItemResponse(
    @Schema(example = "11") long productId,
    @Schema(example = "Bird's Nest Fern") String name,
    @Schema(example = "/images/plants/frn-birdnest.svg") String imagePath,
    @Schema(
            type = "string",
            format = "decimal",
            pattern = "^\\d{1,8}\\.\\d{2}$",
            example = "26.99",
            description = "Current unit price (prices are live, never snapshotted)")
        String unitPrice,
    @Schema(example = "2", minimum = "1", maximum = "10") int quantity,
    @Schema(
            type = "string",
            format = "decimal",
            pattern = "^\\d{1,8}\\.\\d{2}$",
            example = "53.98",
            description = "unitPrice × quantity")
        String lineTotal,
    @Schema(example = "3", minimum = "0") int stock,
    @Schema(
            example = "3",
            minimum = "0",
            maximum = "10",
            description = "min(stock, 10); 0 when out of stock")
        int maxQuantity,
    StockStatus stockStatus) {}
