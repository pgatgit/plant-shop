package com.plantshop.cart.api;

import static com.plantshop.support.OpenApiContract.conformsToContract;
import static com.plantshop.support.TestAuth.githubUser;
import static com.plantshop.support.TestAuth.shopper;
import static com.plantshop.support.TestAuth.withScopes;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plantshop.cart.domain.StockStatus;
import com.plantshop.cart.mapper.CartMapperImpl;
import com.plantshop.cart.service.CartLineView;
import com.plantshop.cart.service.CartService;
import com.plantshop.cart.service.CartView;
import com.plantshop.common.error.ApiException;
import com.plantshop.common.error.ProblemCode;
import com.plantshop.common.web.RequestIdFilter;
import com.plantshop.support.WebMvcSecurityTestConfig;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(CartController.class)
@Import({WebMvcSecurityTestConfig.class, CartMapperImpl.class, RequestIdFilter.class})
class CartControllerTest {

  private static final String OWNER = "local:shopper";

  @Autowired private MockMvc mvc;

  @MockitoBean private CartService cartService;

  private static CartView fernCart(int quantity) {
    BigDecimal price = new BigDecimal("26.99");
    return new CartView(
        List.of(
            new CartLineView(
                11L,
                "Bird's Nest Fern",
                "/images/plants/frn-birdnest.svg",
                price,
                quantity,
                price.multiply(BigDecimal.valueOf(quantity)),
                3,
                3,
                StockStatus.OK)),
        quantity,
        price.multiply(BigDecimal.valueOf(quantity)));
  }

  private static MockHttpServletRequestBuilder json(
      MockHttpServletRequestBuilder builder, String body) {
    return builder.contentType(MediaType.APPLICATION_JSON).content(body);
  }

  @Test
  @Tag("US-07")
  @DisplayName("[AC-07.1] GET /cart returns lines, totals and money as two-decimal strings")
  void getCart_returnsView() throws Exception {
    when(cartService.get(OWNER)).thenReturn(fernCart(2));

    mvc.perform(get("/api/v1/cart").with(shopper()))
        .andExpect(status().isOk())
        .andExpect(conformsToContract())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].productId").value(11))
        .andExpect(jsonPath("$.items[0].unitPrice").value("26.99"))
        .andExpect(jsonPath("$.items[0].lineTotal").value("53.98"))
        .andExpect(jsonPath("$.items[0].maxQuantity").value(3))
        .andExpect(jsonPath("$.items[0].stockStatus").value("OK"))
        .andExpect(jsonPath("$.itemCount").value(2))
        .andExpect(jsonPath("$.subtotal").value("53.98"))
        .andExpect(jsonPath("$.currency").value("USD"));
  }

  @Test
  @Tag("US-07")
  @DisplayName("[AC-07.4] an empty cart has subtotal 0.00")
  void getCart_empty() throws Exception {
    when(cartService.get(OWNER)).thenReturn(CartView.empty());

    mvc.perform(get("/api/v1/cart").with(shopper()))
        .andExpect(status().isOk())
        .andExpect(conformsToContract())
        .andExpect(jsonPath("$.items", hasSize(0)))
        .andExpect(jsonPath("$.subtotal").value("0.00"));
  }

  @Test
  @Tag("BR-13")
  @DisplayName("[BR-13] the owner is taken from the token: GitHub users get github:{id}")
  void owner_comesFromToken() throws Exception {
    when(cartService.get("github:583231")).thenReturn(CartView.empty());

    mvc.perform(get("/api/v1/cart").with(githubUser(583231, "octocat"))).andExpect(status().isOk());

    verify(cartService).get("github:583231");
  }

  @Test
  @Tag("US-06")
  @DisplayName("[AC-06.1] POST /cart/items defaults quantity to 1")
  void add_defaultsQuantity() throws Exception {
    when(cartService.addItem(OWNER, 11L, 1)).thenReturn(fernCart(1));

    mvc.perform(json(post("/api/v1/cart/items"), "{\"productId\":11}").with(shopper()))
        .andExpect(status().isOk())
        .andExpect(conformsToContract())
        .andExpect(jsonPath("$.itemCount").value(1));
  }

  @Test
  @Tag("US-06")
  void add_withQuantity() throws Exception {
    when(cartService.addItem(OWNER, 11L, 2)).thenReturn(fernCart(2));

    mvc.perform(
            json(post("/api/v1/cart/items"), "{\"productId\":11,\"quantity\":2}").with(shopper()))
        .andExpect(status().isOk())
        .andExpect(conformsToContract());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"productId\":0}",
        "{\"productId\":-1}",
        "{\"productId\":11,\"quantity\":0}",
        "{\"productId\":11,\"quantity\":11}"
      })
  @DisplayName("[AC-08.4] invalid add bodies are 400 validation-failed")
  void add_invalidBody(String body) throws Exception {
    mvc.perform(json(post("/api/v1/cart/items"), body).with(shopper()))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("validation-failed"))
        .andExpect(jsonPath("$.errors[0].field").exists());
    verifyNoInteractions(cartService);
  }

  @ParameterizedTest
  @ValueSource(strings = {"not json", "{\"productId\":\"abc\"}", "[]"})
  void add_malformedBody(String body) throws Exception {
    mvc.perform(json(post("/api/v1/cart/items"), body).with(shopper()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("malformed-request"));
    verifyNoInteractions(cartService);
  }

  @Test
  void add_wrongContentType_is415() throws Exception {
    mvc.perform(
            post("/api/v1/cart/items")
                .contentType(MediaType.TEXT_PLAIN)
                .content("productId=11")
                .with(shopper()))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("unsupported-media-type"));
  }

  @Test
  @Tag("US-06")
  @DisplayName("[AC-06.3] limit violations are 409 with limit/requested/productId")
  void add_overLimit_is409() throws Exception {
    when(cartService.addItem(OWNER, 11L, 1))
        .thenThrow(
            new ApiException(
                ProblemCode.QUANTITY_LIMIT_EXCEEDED,
                "You can have at most 3 of Bird's Nest Fern in your cart.",
                Map.of("limit", 3, "requested", 4, "productId", 11L)));

    mvc.perform(json(post("/api/v1/cart/items"), "{\"productId\":11}").with(shopper()))
        .andExpect(status().isConflict())
        .andExpect(conformsToContract())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("quantity-limit-exceeded"))
        .andExpect(jsonPath("$.type").value(ProblemCode.TYPE_BASE + "quantity-limit-exceeded"))
        .andExpect(
            jsonPath("$.detail").value("You can have at most 3 of Bird's Nest Fern in your cart."))
        .andExpect(jsonPath("$.limit").value(3))
        .andExpect(jsonPath("$.requested").value(4))
        .andExpect(jsonPath("$.productId").value(11))
        .andExpect(jsonPath("$.instance").value("/api/v1/cart/items"));
  }

  @Test
  @Tag("US-06")
  @DisplayName("[AC-06.6] unknown products are 404 product-not-found")
  void add_unknownProduct_is404() throws Exception {
    when(cartService.addItem(anyString(), anyLong(), anyInt()))
        .thenThrow(
            new ApiException(ProblemCode.PRODUCT_NOT_FOUND, "This plant is no longer available."));

    mvc.perform(json(post("/api/v1/cart/items"), "{\"productId\":999}").with(shopper()))
        .andExpect(status().isNotFound())
        .andExpect(conformsToContract())
        .andExpect(jsonPath("$.code").value("product-not-found"));
  }

  @Test
  @Tag("US-08")
  @DisplayName("[AC-08.2] PATCH sets the quantity and returns the cart")
  void update_setsQuantity() throws Exception {
    when(cartService.updateQuantity(OWNER, 11L, 3)).thenReturn(fernCart(3));

    mvc.perform(json(patch("/api/v1/cart/items/11"), "{\"quantity\":3}").with(shopper()))
        .andExpect(status().isOk())
        .andExpect(conformsToContract())
        .andExpect(jsonPath("$.items[0].quantity").value(3));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"quantity\":0}", "{\"quantity\":11}", "{\"quantity\":null}"})
  @DisplayName("[AC-08.4] quantity must be 1–10; 0 is rejected (use DELETE)")
  void update_invalidQuantity_is400(String body) throws Exception {
    mvc.perform(json(patch("/api/v1/cart/items/11"), body).with(shopper()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-failed"))
        .andExpect(jsonPath("$.errors[0].field").value("quantity"));
    verifyNoInteractions(cartService);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-5"})
  void update_nonPositiveProductId_is400(String id) throws Exception {
    mvc.perform(json(patch("/api/v1/cart/items/" + id), "{\"quantity\":1}").with(shopper()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-failed"));
    verifyNoInteractions(cartService);
  }

  @Test
  void update_nonNumericProductId_is400() throws Exception {
    mvc.perform(json(patch("/api/v1/cart/items/abc"), "{\"quantity\":1}").with(shopper()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("malformed-request"));
  }

  @Test
  @Tag("US-08")
  void update_missingLine_is404() throws Exception {
    when(cartService.updateQuantity(OWNER, 11L, 2))
        .thenThrow(
            new ApiException(
                ProblemCode.CART_ITEM_NOT_FOUND,
                "This plant is not in your cart.",
                Map.of("productId", 11L)));

    mvc.perform(json(patch("/api/v1/cart/items/11"), "{\"quantity\":2}").with(shopper()))
        .andExpect(status().isNotFound())
        .andExpect(conformsToContract())
        .andExpect(jsonPath("$.code").value("cart-item-not-found"));
  }

  @Test
  @Tag("US-09")
  @DisplayName("[AC-09.1] DELETE removes the line and returns the cart")
  void remove_returnsCart() throws Exception {
    when(cartService.removeItem(OWNER, 11L)).thenReturn(CartView.empty());

    mvc.perform(delete("/api/v1/cart/items/11").with(shopper()))
        .andExpect(status().isOk())
        .andExpect(conformsToContract())
        .andExpect(jsonPath("$.itemCount").value(0));
  }

  @Test
  @DisplayName("[ADR-006] concurrent modification is 409")
  void remove_concurrent_is409() throws Exception {
    when(cartService.removeItem(OWNER, 11L))
        .thenThrow(
            new ApiException(
                ProblemCode.CONCURRENT_MODIFICATION,
                "Your cart was changed by another request at the same time. Please try again."));

    mvc.perform(delete("/api/v1/cart/items/11").with(shopper()))
        .andExpect(status().isConflict())
        .andExpect(conformsToContract())
        .andExpect(jsonPath("$.code").value("concurrent-modification"));
  }

  @Test
  @Tag("BR-01")
  @DisplayName("[NFR-SEC-01] every cart endpoint is 401 without a token, with X-Request-Id")
  void unauthenticated_is401() throws Exception {
    for (MockHttpServletRequestBuilder request :
        List.of(
            get("/api/v1/cart"),
            json(post("/api/v1/cart/items"), "{\"productId\":11}"),
            json(patch("/api/v1/cart/items/11"), "{\"quantity\":1}"),
            delete("/api/v1/cart/items/11"))) {
      mvc.perform(request)
          .andExpect(status().isUnauthorized())
          .andExpect(header().exists("X-Request-Id"))
          .andExpect(jsonPath("$.code").value("unauthenticated"));
    }
    verifyNoInteractions(cartService);
  }

  @Test
  @Tag("NFR-SEC-02")
  @DisplayName("[NFR-SEC-02] catalog-only tokens cannot read or change the cart")
  void viewer_is403() throws Exception {
    for (MockHttpServletRequestBuilder request :
        List.of(
            get("/api/v1/cart"),
            json(post("/api/v1/cart/items"), "{\"productId\":11}"),
            json(patch("/api/v1/cart/items/11"), "{\"quantity\":1}"),
            delete("/api/v1/cart/items/11"))) {
      mvc.perform(request.with(withScopes("viewer", "catalog.read")))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("forbidden"));
    }
    verifyNoInteractions(cartService);
  }

  @Test
  @Tag("NFR-SEC-02")
  @DisplayName("[NFR-SEC-02] cart.read alone can read but not change the cart")
  void readOnly_canReadButNotWrite() throws Exception {
    when(cartService.get("local:reader")).thenReturn(CartView.empty());

    mvc.perform(get("/api/v1/cart").with(withScopes("reader", "cart.read")))
        .andExpect(status().isOk());
    mvc.perform(
            json(post("/api/v1/cart/items"), "{\"productId\":11}")
                .with(withScopes("reader", "cart.read")))
        .andExpect(status().isForbidden());
  }
}
