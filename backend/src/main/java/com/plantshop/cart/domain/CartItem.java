package com.plantshop.cart.domain;

import com.plantshop.catalog.domain.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One product line in a cart. No price is stored: prices are always live (BR-09). */
@Entity
@Table(name = "cart_item")
public class CartItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "cart_id", nullable = false, updatable = false)
  private Cart cart;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false, updatable = false)
  private Product product;

  @JdbcTypeCode(SqlTypes.SMALLINT)
  @Column(nullable = false)
  private int quantity;

  /** Required by JPA. */
  protected CartItem() {}

  /** Callers validate the quantity first, so the constructor cannot fail half-way. */
  CartItem(Cart cart, Product product, int quantity) {
    this.cart = cart;
    this.product = product;
    this.quantity = quantity;
  }

  /**
   * Sets the quantity. Stock limits are checked by the service; this only guards the schema range.
   *
   * @param newQuantity the new quantity (1–10)
   * @throws IllegalArgumentException if outside 1–10
   */
  public void changeQuantity(int newQuantity) {
    this.quantity = requireValidQuantity(newQuantity);
  }

  /**
   * Checks the schema range of a line quantity ({@code ck_cart_item_quantity}).
   *
   * @param quantity the quantity
   * @return the quantity
   * @throws IllegalArgumentException if outside 1–10
   */
  static int requireValidQuantity(int quantity) {
    if (quantity < LineLimit.MIN_PER_LINE || quantity > LineLimit.MAX_PER_LINE) {
      throw new IllegalArgumentException("Quantity must be between 1 and 10: " + quantity);
    }
    return quantity;
  }

  public Long getId() {
    return id;
  }

  public Cart getCart() {
    return cart;
  }

  public Product getProduct() {
    return product;
  }

  public int getQuantity() {
    return quantity;
  }
}
