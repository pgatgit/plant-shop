package com.plantshop.cart.domain;

/**
 * Maximum quantity of one product per cart: {@code min(stock, 10)} (BR-08, ADR-006).
 *
 * @param max the highest allowed quantity; 0 when the product is out of stock
 */
public record LineLimit(int max) {

  /** Hard cap per line, independent of stock (also enforced by {@code ck_cart_item_quantity}). */
  public static final int MAX_PER_LINE = 10;

  /** Smallest quantity a line can hold; removing a line is a separate operation. */
  public static final int MIN_PER_LINE = 1;

  /**
   * Computes the limit for the given stock.
   *
   * @param stock units currently in stock
   * @return the limit
   */
  public static LineLimit of(int stock) {
    return new LineLimit(Math.max(0, Math.min(stock, MAX_PER_LINE)));
  }

  /**
   * Tells whether a line may hold the given quantity.
   *
   * @param quantity the requested line quantity
   * @return {@code true} if {@code 1 <= quantity <= max}
   */
  public boolean allows(int quantity) {
    return quantity >= MIN_PER_LINE && quantity <= max;
  }
}
