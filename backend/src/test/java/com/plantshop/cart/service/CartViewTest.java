package com.plantshop.cart.service;

import static com.plantshop.support.TestProducts.product;
import static org.assertj.core.api.Assertions.assertThat;

import com.plantshop.cart.domain.Cart;
import com.plantshop.cart.domain.StockStatus;
import com.plantshop.catalog.domain.Product;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@Tag("US-07")
class CartViewTest {

  @Test
  @DisplayName("[AC-07.4] an empty cart has no lines, zero items and a 0.00 subtotal")
  void empty() {
    CartView view = CartView.empty();

    assertThat(view.lines()).isEmpty();
    assertThat(view.itemCount()).isZero();
    assertThat(view.subtotal()).isEqualByComparingTo("0").hasScaleOf(2);
  }

  @Test
  @DisplayName("[AC-07.1][AC-07.5][BR-10] line totals, item count and subtotal")
  void of_computesTotals() {
    Cart cart = Cart.forOwner("local:shopper");
    cart.addLine(product(11, "Bird's Nest Fern", "26.99", 3), 2);
    cart.addLine(product(13, "Spider Plant", "12.99", 50), 3);

    CartView view = CartView.of(cart);

    assertThat(view.lines()).hasSize(2);
    CartLineView fern = view.lines().get(0);
    assertThat(fern.productId()).isEqualTo(11L);
    assertThat(fern.name()).isEqualTo("Bird's Nest Fern");
    assertThat(fern.imagePath()).isEqualTo("/images/plants/sku-11.svg");
    assertThat(fern.unitPrice()).isEqualByComparingTo("26.99");
    assertThat(fern.quantity()).isEqualTo(2);
    assertThat(fern.lineTotal()).isEqualTo(new BigDecimal("53.98"));
    assertThat(fern.stock()).isEqualTo(3);
    assertThat(fern.maxQuantity()).isEqualTo(3);
    assertThat(fern.stockStatus()).isEqualTo(StockStatus.OK);
    assertThat(view.lines().get(1).lineTotal()).isEqualTo(new BigDecimal("38.97"));
    assertThat(view.itemCount()).isEqualTo(5);
    assertThat(view.subtotal()).isEqualTo(new BigDecimal("92.95"));
  }

  @Test
  @DisplayName("[AC-07.2][BR-09] prices are read live from the product")
  void of_usesCurrentPrice() {
    Product fern = product(11, "Bird's Nest Fern", "26.99", 3);
    Cart cart = Cart.forOwner("local:shopper");
    cart.addLine(fern, 1);
    ReflectionTestUtils.setField(fern, "price", new BigDecimal("30.00"));

    assertThat(CartView.of(cart).subtotal()).isEqualTo(new BigDecimal("30.00"));
  }

  @Test
  @DisplayName("[AC-07.3][BR-12] lines report stock problems without being modified")
  void of_reportsStockStatus() {
    Product fern = product(11, "Bird's Nest Fern", "26.99", 3);
    Product maidenhair = product(12, "Maidenhair Fern", "19.99", 5);
    Cart cart = Cart.forOwner("local:shopper");
    cart.addLine(fern, 3);
    cart.addLine(maidenhair, 2);
    ReflectionTestUtils.setField(fern, "stock", 2);
    ReflectionTestUtils.setField(maidenhair, "stock", 0);

    CartView view = CartView.of(cart);

    assertThat(view.lines().get(0).stockStatus()).isEqualTo(StockStatus.INSUFFICIENT);
    assertThat(view.lines().get(0).quantity()).isEqualTo(3);
    assertThat(view.lines().get(0).maxQuantity()).isEqualTo(2);
    assertThat(view.lines().get(1).stockStatus()).isEqualTo(StockStatus.OUT_OF_STOCK);
    assertThat(view.lines().get(1).maxQuantity()).isZero();
  }

  @Test
  @DisplayName("[BR-10] line totals are rounded HALF_UP to two decimals")
  void of_roundsHalfUp() {
    Product odd = product(20, "Odd Price", "0.335", 10);
    Cart cart = Cart.forOwner("local:shopper");
    cart.addLine(odd, 1);

    assertThat(CartView.of(cart).lines().get(0).lineTotal()).isEqualTo(new BigDecimal("0.34"));
  }
}
