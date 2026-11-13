package com.plantshop.catalog.api.dto;

import com.plantshop.catalog.domain.LightRequirement;
import com.plantshop.catalog.domain.WateringFrequency;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A catalog product as shown on a product card.
 *
 * @param id product id
 * @param sku stock-keeping unit
 * @param name display name
 * @param description 2–3 sentence care description
 * @param price unit price as a decimal string with two decimals
 * @param currency ISO 4217 currency code (always USD)
 * @param stock units currently in stock
 * @param category the product's category
 * @param light light requirement
 * @param watering watering frequency
 * @param petSafe whether the plant is non-toxic to cats and dogs
 * @param imagePath path of the illustration, relative to the SPA origin
 */
@Schema(name = "Product")
public record ProductResponse(
    @Schema(example = "11") long id,
    @Schema(example = "FRN-BIRDNEST") String sku,
    @Schema(example = "Bird's Nest Fern") String name,
    @Schema(example = "Glossy, wavy fronds that unfurl from a central rosette like a nest.")
        String description,
    @Schema(
            type = "string",
            format = "decimal",
            pattern = "^\\d{1,8}\\.\\d{2}$",
            example = "26.99",
            description = "Unit price, always two decimals")
        String price,
    @Schema(example = "USD", allowableValues = "USD") String currency,
    @Schema(example = "3", minimum = "0") int stock,
    CategoryResponse category,
    LightRequirement light,
    WateringFrequency watering,
    @Schema(example = "true") boolean petSafe,
    @Schema(example = "/images/plants/frn-birdnest.svg") String imagePath) {}
