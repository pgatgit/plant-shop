package com.plantshop.support;

import org.springframework.jdbc.core.JdbcTemplate;

/** Database helpers for cart integration tests. Tests resolve products by SKU, never by row id. */
public final class CartFixtures {

  private CartFixtures() {}

  /**
   * Looks up a seeded product id.
   *
   * @param jdbc JDBC access
   * @param sku seed SKU, e.g. {@code FRN-BIRDNEST}
   * @return the product id
   */
  public static long productId(JdbcTemplate jdbc, String sku) {
    Long id = jdbc.queryForObject("SELECT id FROM product WHERE sku = ?", Long.class, sku);
    if (id == null) {
      throw new IllegalStateException("Unknown SKU " + sku);
    }
    return id;
  }

  /**
   * Deletes every cart (lines cascade).
   *
   * @param jdbc JDBC access
   */
  public static void deleteAllCarts(JdbcTemplate jdbc) {
    jdbc.update("DELETE FROM cart");
  }

  /**
   * Counts rows of a table.
   *
   * @param jdbc JDBC access
   * @param table {@code cart} or {@code cart_item}
   * @return the row count
   */
  public static int count(JdbcTemplate jdbc, String table) {
    if (!"cart".equals(table) && !"cart_item".equals(table)) {
      throw new IllegalArgumentException(table);
    }
    Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    return n == null ? 0 : n;
  }
}
