package com.plantshop.cart.api;

import static com.plantshop.support.CartFixtures.deleteAllCarts;
import static com.plantshop.support.CartFixtures.productId;
import static com.plantshop.support.OpenApiContract.conformsToContract;
import static com.plantshop.support.TestAuth.shopper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plantshop.support.MySqlTestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Cart behaviour when the catalog changes after items were added: live prices (BR-09) and stock
 * warnings (BR-12). Changes the seeded Bird's Nest Fern and restores it after each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestcontainersConfiguration.class)
@Tag("US-07")
class CartLiveCatalogIT {

  private static final String SKU = "FRN-BIRDNEST";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private long fern;
  private String originalPrice;
  private int originalStock;

  @BeforeEach
  void setUp() {
    deleteAllCarts(jdbc);
    fern = productId(jdbc, SKU);
    originalPrice =
        jdbc.queryForObject("SELECT price FROM product WHERE sku = ?", String.class, SKU);
    Integer stock =
        jdbc.queryForObject("SELECT stock FROM product WHERE sku = ?", Integer.class, SKU);
    originalStock = stock == null ? 0 : stock;
  }

  @AfterEach
  void restore() {
    deleteAllCarts(jdbc);
    jdbc.update(
        "UPDATE product SET price = ?, stock = ? WHERE sku = ?", originalPrice, originalStock, SKU);
  }

  private void addFern(int quantity) throws Exception {
    mvc.perform(
            post("/api/v1/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + fern + ",\"quantity\":" + quantity + "}")
                .with(shopper()))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("[AC-07.2][BR-09] a price change shows up in the cart without notice")
  void priceChange_isReflected() throws Exception {
    addFern(2);
    jdbc.update("UPDATE product SET price = 30.50 WHERE sku = ?", SKU);

    mvc.perform(get("/api/v1/cart").with(shopper()))
        .andExpect(status().isOk())
        .andExpect(conformsToContract())
        .andExpect(jsonPath("$.items[0].unitPrice").value("30.50"))
        .andExpect(jsonPath("$.items[0].lineTotal").value("61.00"))
        .andExpect(jsonPath("$.subtotal").value("61.00"));
  }

  @Test
  @DisplayName("[AC-07.3][BR-12] stock below the line quantity is INSUFFICIENT; line unchanged")
  void stockDrop_isInsufficient() throws Exception {
    addFern(3);
    jdbc.update("UPDATE product SET stock = 2 WHERE sku = ?", SKU);

    mvc.perform(get("/api/v1/cart").with(shopper()))
        .andExpect(conformsToContract())
        .andExpect(jsonPath("$.items[0].quantity").value(3))
        .andExpect(jsonPath("$.items[0].stock").value(2))
        .andExpect(jsonPath("$.items[0].maxQuantity").value(2))
        .andExpect(jsonPath("$.items[0].stockStatus").value("INSUFFICIENT"));
  }

  @Test
  @DisplayName("[AC-07.3] reducing an over-stock line is allowed, even if still above stock")
  void stockDrop_reduceIsAllowed() throws Exception {
    addFern(3);
    jdbc.update("UPDATE product SET stock = 1 WHERE sku = ?", SKU);

    mvc.perform(
            patch("/api/v1/cart/items/" + fern)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":2}")
                .with(shopper()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].quantity").value(2))
        .andExpect(jsonPath("$.items[0].stockStatus").value("INSUFFICIENT"));
  }

  @Test
  @DisplayName("[AC-07.3][BR-12] sold-out lines are OUT_OF_STOCK and cannot be increased")
  void soldOut_isOutOfStock() throws Exception {
    addFern(1);
    jdbc.update("UPDATE product SET stock = 0 WHERE sku = ?", SKU);

    mvc.perform(get("/api/v1/cart").with(shopper()))
        .andExpect(jsonPath("$.items[0].stockStatus").value("OUT_OF_STOCK"))
        .andExpect(jsonPath("$.items[0].maxQuantity").value(0));
    mvc.perform(
            patch("/api/v1/cart/items/" + fern)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":2}")
                .with(shopper()))
        .andExpect(status().isConflict())
        .andExpect(conformsToContract())
        .andExpect(jsonPath("$.code").value("out-of-stock"));
  }
}
