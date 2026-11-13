package com.plantshop.cart.service;

import com.plantshop.cart.domain.Cart;
import com.plantshop.cart.domain.CartItem;
import com.plantshop.cart.domain.LineLimit;
import com.plantshop.cart.domain.StockStatus;
import com.plantshop.catalog.domain.Product;
import com.plantshop.common.money.Money;
import java.math.BigDecimal;
import java.util.List;

/**
 * Read model of a cart, computed at read time from current product prices and stock (BR-09, BR-10,
 * BR-12).
 *
 * @param lines the lines in the order they were added
 * @param itemCount sum of all line quantities
 * @param subtotal sum of all line totals, scale 2
 */
public record CartView(List<CartLineView> lines, int itemCount, BigDecimal subtotal) {

  /** Defensive copy of the lines. */
  public CartView {
    lines = List.copyOf(lines);
  }

  /**
   * The view of an owner without a cart.
   *
   * @return an empty view
   */
  public static CartView empty() {
    return new CartView(List.of(), 0, Money.normalize(BigDecimal.ZERO));
  }

  /**
   * Builds the view of a cart.
   *
   * @param cart the cart with its lines and products loaded
   * @return the view
   */
  public static CartView of(Cart cart) {
    List<CartLineView> lines = cart.getItems().stream().map(CartView::line).toList();
    int itemCount = lines.stream().mapToInt(CartLineView::quantity).sum();
    BigDecimal subtotal =
        Money.normalize(
            lines.stream().map(CartLineView::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add));
    return new CartView(lines, itemCount, subtotal);
  }

  private static CartLineView line(CartItem item) {
    Product product = item.getProduct();
    int quantity = item.getQuantity();
    int stock = product.getStock();
    return new CartLineView(
        product.getId(),
        product.getName(),
        product.getImagePath(),
        product.getPrice(),
        quantity,
        Money.normalize(product.getPrice().multiply(BigDecimal.valueOf(quantity))),
        stock,
        LineLimit.of(stock).max(),
        StockStatus.of(quantity, stock));
  }
}
