package com.plantshop.cart.domain;

/** How a cart line relates to current stock (BR-12). Lines are never modified automatically. */
public enum StockStatus {
  /** Enough stock for the line's quantity. */
  OK,
  /** Some stock, but less than the line's quantity. */
  INSUFFICIENT,
  /** No stock at all. */
  OUT_OF_STOCK;

  /**
   * Classifies a line.
   *
   * @param quantity the line quantity
   * @param stock units currently in stock
   * @return the status
   */
  public static StockStatus of(int quantity, int stock) {
    if (stock <= 0) {
      return OUT_OF_STOCK;
    }
    return quantity > stock ? INSUFFICIENT : OK;
  }
}
