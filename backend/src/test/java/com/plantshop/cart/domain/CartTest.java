package com.plantshop.cart.domain;

import static com.plantshop.support.TestProducts.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.plantshop.catalog.domain.Product;
import org.junit.jupiter.api.Test;

class CartTest {

  private final Product fern = product(11, "Bird's Nest Fern", "26.99", 3);
  private final Product spider = product(13, "Spider Plant", "12.99", 50);

  @Test
  void newCart_isEmptyAndOwned() {
    Cart cart = Cart.forOwner("local:shopper");

    assertThat(cart.getOwnerId()).isEqualTo("local:shopper");
    assertThat(cart.getItems()).isEmpty();
    assertThat(cart.line(11L)).isEmpty();
  }

  @Test
  void addLine_keepsInsertionOrderAndIsFoundByProduct() {
    Cart cart = Cart.forOwner("local:shopper");

    cart.addLine(fern, 2);
    cart.addLine(spider, 1);

    assertThat(cart.getItems()).extracting(CartItem::getProduct).containsExactly(fern, spider);
    assertThat(cart.line(11L)).get().extracting(CartItem::getQuantity).isEqualTo(2);
    assertThat(cart.line(11L).orElseThrow().getCart()).isSameAs(cart);
  }

  @Test
  void addLine_twiceForTheSameProduct_isRejected() {
    Cart cart = Cart.forOwner("local:shopper");
    cart.addLine(fern, 1);

    assertThatThrownBy(() -> cart.addLine(fern, 1)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void removeLine_dropsIt() {
    Cart cart = Cart.forOwner("local:shopper");
    CartItem line = cart.addLine(fern, 1);

    cart.removeLine(line);

    assertThat(cart.getItems()).isEmpty();
  }

  @Test
  void quantity_mustStayWithinTheSchemaRange() {
    Cart cart = Cart.forOwner("local:shopper");
    CartItem line = cart.addLine(fern, 1);

    line.changeQuantity(10);
    assertThat(line.getQuantity()).isEqualTo(10);
    assertThatThrownBy(() -> line.changeQuantity(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> line.changeQuantity(11)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> cart.addLine(spider, 0)).isInstanceOf(IllegalArgumentException.class);
  }
}
