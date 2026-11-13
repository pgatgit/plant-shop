package com.plantshop.cart.api;

import static com.plantshop.support.CartFixtures.count;
import static com.plantshop.support.CartFixtures.deleteAllCarts;
import static com.plantshop.support.CartFixtures.productId;
import static com.plantshop.support.TestAuth.githubUser;
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
import java.util.List;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** BR-13 / threat T11: a shopper can never see or change another shopper's cart. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestcontainersConfiguration.class)
@Tag("BR-13")
class CartOwnerIsolationIT {

  private static final RequestPostProcessor ALICE = shopper();
  private static final RequestPostProcessor BOB = githubUser(583231, "octocat");

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private long fern;
  private long spider;

  @BeforeEach
  void setUp() {
    deleteAllCarts(jdbc);
    fern = productId(jdbc, "FRN-BIRDNEST");
    spider = productId(jdbc, "TRL-SPIDER");
  }

  @AfterEach
  void cleanUp() {
    deleteAllCarts(jdbc);
  }

  private void add(RequestPostProcessor user, long productId) throws Exception {
    mvc.perform(
            post("/api/v1/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + productId + "}")
                .with(user))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("[BR-13] carts are separate per owner and other owners' lines are invisible")
  void carts_areIsolated() throws Exception {
    add(ALICE, fern);

    mvc.perform(get("/api/v1/cart").with(BOB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(0)));

    add(BOB, spider);

    mvc.perform(get("/api/v1/cart").with(ALICE))
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].productId").value(fern));
    mvc.perform(get("/api/v1/cart").with(BOB))
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].productId").value(spider));
    assertThat(jdbc.queryForList("SELECT owner_id FROM cart ORDER BY owner_id", String.class))
        .isEqualTo(List.of("github:583231", "local:shopper"));
  }

  @Test
  @DisplayName("[BR-13] another owner cannot update or remove my line: 404, my cart unchanged")
  void otherOwner_cannotModify() throws Exception {
    add(ALICE, fern);
    add(BOB, spider);

    mvc.perform(
            patch("/api/v1/cart/items/" + fern)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":2}")
                .with(BOB))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("cart-item-not-found"));
    mvc.perform(delete("/api/v1/cart/items/" + fern).with(BOB)).andExpect(status().isNotFound());

    mvc.perform(get("/api/v1/cart").with(ALICE))
        .andExpect(jsonPath("$.items[0].quantity").value(1));
    assertThat(count(jdbc, "cart_item")).isEqualTo(2);
  }

  @Test
  @DisplayName("[BR-13] the owner id in the body or query is ignored")
  void ownerInRequest_isIgnored() throws Exception {
    mvc.perform(
            post("/api/v1/cart/items")
                .param("ownerId", "local:shopper")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + fern + ",\"ownerId\":\"local:shopper\"}")
                .with(BOB))
        .andExpect(status().isOk());

    assertThat(jdbc.queryForList("SELECT owner_id FROM cart", String.class))
        .containsExactly("github:583231");
  }
}
