package com.plantshop.cart.api;

import static com.plantshop.support.CartFixtures.count;
import static com.plantshop.support.CartFixtures.deleteAllCarts;
import static com.plantshop.support.CartFixtures.productId;
import static com.plantshop.support.OpenApiContract.conformsToContract;
import static com.plantshop.support.TestAuth.shopper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plantshop.support.MySqlTestcontainersConfiguration;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * Cart API end to end through the full Spring context on real MySQL (US-06…US-09); every exchange
 * is checked against the committed OpenAPI contract. Uses the seed catalog unchanged.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestcontainersConfiguration.class)
class CartIT {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MeterRegistry meters;

  private long fern; // FRN-BIRDNEST: 26.99, stock 3
  private long maidenhair; // FRN-MAIDENHAIR: stock 0
  private long spider; // TRL-SPIDER: 12.99, stock 50

  @BeforeEach
  void setUp() {
    deleteAllCarts(jdbc);
    fern = productId(jdbc, "FRN-BIRDNEST");
    maidenhair = productId(jdbc, "FRN-MAIDENHAIR");
    spider = productId(jdbc, "TRL-SPIDER");
  }

  @AfterEach
  void cleanUp() {
    deleteAllCarts(jdbc);
  }

  private ResultActions add(long productId, Integer quantity) throws Exception {
    String body =
        quantity == null
            ? "{\"productId\":" + productId + "}"
            : "{\"productId\":" + productId + ",\"quantity\":" + quantity + "}";
    return mvc.perform(
            post("/api/v1/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(shopper()))
        .andExpect(conformsToContract());
  }

  private ResultActions update(long productId, int quantity) throws Exception {
    return mvc.perform(
            patch("/api/v1/cart/items/" + productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":" + quantity + "}")
                .with(shopper()))
        .andExpect(conformsToContract());
  }

  private ResultActions remove(long productId) throws Exception {
    return mvc.perform(delete("/api/v1/cart/items/" + productId).with(shopper()))
        .andExpect(conformsToContract());
  }

  private ResultActions getCart() throws Exception {
    return mvc.perform(get("/api/v1/cart").with(shopper())).andExpect(conformsToContract());
  }

  private double mutations(String op, String outcome) {
    Counter counter =
        meters.find("plantshop.cart.mutations").tag("op", op).tag("outcome", outcome).counter();
    return counter == null ? 0 : counter.count();
  }

  @Test
  @Tag("US-07")
  @DisplayName("[AC-07.4][BR-06] reading before any add is an empty cart and creates no row")
  void get_withoutCart_isEmptyAndCreatesNothing() throws Exception {
    getCart()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(0)))
        .andExpect(jsonPath("$.itemCount").value(0))
        .andExpect(jsonPath("$.subtotal").value("0.00"))
        .andExpect(jsonPath("$.currency").value("USD"));

    assertThat(count(jdbc, "cart")).isZero();
  }

  @Test
  @Tag("US-06")
  @DisplayName("[AC-06.1][AC-06.2][BR-07] add creates the cart; re-add increments the same line")
  void add_thenReAdd_incrementsOneLine() throws Exception {
    double before = mutations("add", "success");

    add(fern, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].productId").value(fern))
        .andExpect(jsonPath("$.items[0].name").value("Bird's Nest Fern"))
        .andExpect(jsonPath("$.items[0].quantity").value(1))
        .andExpect(jsonPath("$.itemCount").value(1));
    add(fern, 1)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].quantity").value(2))
        .andExpect(jsonPath("$.items[0].lineTotal").value("53.98"))
        .andExpect(jsonPath("$.subtotal").value("53.98"));

    assertThat(count(jdbc, "cart")).isEqualTo(1);
    assertThat(count(jdbc, "cart_item")).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT owner_id FROM cart", String.class))
        .isEqualTo("local:shopper");
    assertThat(mutations("add", "success") - before).isEqualTo(2);
  }

  @Test
  @Tag("US-07")
  @DisplayName("[AC-07.1][AC-07.5] lines keep insertion order; totals cover all lines")
  void view_listsLinesWithTotals() throws Exception {
    add(spider, 3);
    add(fern, 1);

    getCart()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].name").value("Spider Plant"))
        .andExpect(jsonPath("$.items[0].unitPrice").value("12.99"))
        .andExpect(jsonPath("$.items[0].lineTotal").value("38.97"))
        .andExpect(jsonPath("$.items[0].maxQuantity").value(10))
        .andExpect(jsonPath("$.items[0].stockStatus").value("OK"))
        .andExpect(jsonPath("$.items[0].imagePath").value("/images/plants/trl-spider.svg"))
        .andExpect(jsonPath("$.items[1].name").value("Bird's Nest Fern"))
        .andExpect(jsonPath("$.items[1].maxQuantity").value(3))
        .andExpect(jsonPath("$.itemCount").value(4))
        .andExpect(jsonPath("$.subtotal").value("65.96"));
  }

  @Test
  @Tag("US-06")
  @DisplayName("[AC-06.3][BR-08] exceeding min(stock,10) is 409 and the cart is unchanged")
  void add_overStockLimit_is409() throws Exception {
    add(fern, 3).andExpect(status().isOk());

    add(fern, 1)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("quantity-limit-exceeded"))
        .andExpect(
            jsonPath("$.detail").value("You can have at most 3 of Bird's Nest Fern in your cart."))
        .andExpect(jsonPath("$.limit").value(3))
        .andExpect(jsonPath("$.requested").value(4))
        .andExpect(jsonPath("$.productId").value(fern));

    getCart().andExpect(jsonPath("$.items[0].quantity").value(3));
  }

  @Test
  @Tag("US-06")
  @DisplayName("[BR-08] the hard cap of 10 applies even with stock 50")
  void add_overTen_is409() throws Exception {
    add(spider, 10).andExpect(status().isOk());

    add(spider, 1)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.limit").value(10))
        .andExpect(jsonPath("$.requested").value(11));
  }

  @Test
  @Tag("US-06")
  @DisplayName("[AC-06.4][BR-08] out-of-stock products cannot be added")
  void add_outOfStock_is409() throws Exception {
    add(maidenhair, null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("out-of-stock"))
        .andExpect(jsonPath("$.detail").value("Maidenhair Fern is out of stock."));

    assertThat(count(jdbc, "cart_item")).isZero();
  }

  @Test
  @Tag("US-06")
  @DisplayName("[AC-06.6] a product that does not exist is 404 product-not-found")
  void add_unknownProduct_is404() throws Exception {
    add(999_999L, null)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("product-not-found"))
        .andExpect(jsonPath("$.detail").value("This plant is no longer available."));
  }

  @Test
  @Tag("US-08")
  @DisplayName("[AC-08.1][AC-08.2] quantity updates within the limit; totals come back")
  void update_withinLimit() throws Exception {
    add(spider, 1);

    update(spider, 7)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].quantity").value(7))
        .andExpect(jsonPath("$.subtotal").value("90.93"));
    update(spider, 2).andExpect(status().isOk()).andExpect(jsonPath("$.itemCount").value(2));
  }

  @Test
  @Tag("US-08")
  @DisplayName("[AC-08.3] increasing past the limit is 409 and keeps the previous quantity")
  void update_overLimit_is409() throws Exception {
    add(fern, 2);

    update(fern, 4)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("quantity-limit-exceeded"))
        .andExpect(jsonPath("$.limit").value(3));
    getCart().andExpect(jsonPath("$.items[0].quantity").value(2));
  }

  @Test
  @Tag("US-08")
  @DisplayName("[AC-08.4] quantity 0 is rejected; DELETE removes lines")
  void update_toZero_is400() throws Exception {
    add(fern, 1);

    // Invalid by contract, so not contract-validated.
    mvc.perform(
            patch("/api/v1/cart/items/" + fern)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":0}")
                .with(shopper()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-failed"))
        .andExpect(jsonPath("$.errors[0].field").value("quantity"));
  }

  @Test
  @Tag("US-08")
  void update_productNotInCart_is404() throws Exception {
    add(fern, 1);

    update(spider, 1)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("cart-item-not-found"));
  }

  @Test
  @Tag("US-09")
  @DisplayName("[AC-09.1][AC-09.3] remove deletes the line; removing again is 404")
  void remove_thenRemoveAgain() throws Exception {
    add(fern, 1);
    add(spider, 1);

    remove(fern)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].productId").value(spider));
    remove(fern)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("cart-item-not-found"));
    remove(spider).andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)));

    assertThat(count(jdbc, "cart")).as("the empty cart is kept").isEqualTo(1);
    assertThat(count(jdbc, "cart_item")).isZero();
  }

  @Test
  @Tag("US-09")
  void remove_withoutCart_is404() throws Exception {
    remove(fern).andExpect(status().isNotFound());
    assertThat(count(jdbc, "cart")).isZero();
  }

  @Test
  @DisplayName("[ADR-006] every mutation increments the cart version, even line-only changes")
  void mutations_bumpCartVersion() throws Exception {
    add(spider, 1);
    long v1 = version();
    update(spider, 2);
    long v2 = version();
    add(fern, 1);
    long v3 = version();
    remove(fern);
    long v4 = version();

    assertThat(v2).isGreaterThan(v1);
    assertThat(v3).isGreaterThan(v2);
    assertThat(v4).isGreaterThan(v3);
  }

  private long version() {
    Long version = jdbc.queryForObject("SELECT version FROM cart", Long.class);
    return version == null ? -1 : version;
  }
}
