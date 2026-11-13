package com.plantshop.cart.domain;

import com.plantshop.catalog.domain.Product;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A shopper's cart: one per owner, created lazily on the first add and never expired (BR-06). The
 * version is incremented on every mutation, so concurrent changes are detected (ADR-006). The
 * {@code created_at}/{@code updated_at} audit columns are maintained by MySQL.
 */
@Entity
@Table(name = "cart")
public class Cart {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "owner_id", nullable = false, updatable = false, length = 64)
  private String ownerId;

  @Version
  @Column(nullable = false)
  private long version;

  @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("id")
  private List<CartItem> items = new ArrayList<>();

  /** Required by JPA. */
  protected Cart() {}

  private Cart(String ownerId) {
    this.ownerId = ownerId;
  }

  /**
   * Creates an empty cart for an owner.
   *
   * @param ownerId {@code github:{id}} or {@code local:{username}}
   * @return the new, not yet persisted cart
   */
  public static Cart forOwner(String ownerId) {
    return new Cart(Objects.requireNonNull(ownerId, "ownerId"));
  }

  /**
   * Finds the line for a product.
   *
   * @param productId the product id
   * @return the line, if the product is in the cart
   */
  public Optional<CartItem> line(Long productId) {
    return items.stream().filter(item -> item.getProduct().getId().equals(productId)).findFirst();
  }

  /**
   * Adds a new line. Limits are the service's concern; this only guards structural invariants.
   *
   * @param product the product
   * @param quantity the initial quantity (1–10)
   * @return the new line
   * @throws IllegalStateException if the product already has a line
   */
  public CartItem addLine(Product product, int quantity) {
    if (line(product.getId()).isPresent()) {
      throw new IllegalStateException("Product " + product.getId() + " already has a line");
    }
    CartItem item = new CartItem(this, product, CartItem.requireValidQuantity(quantity));
    items.add(item);
    return item;
  }

  /**
   * Removes a line.
   *
   * @param item the line to remove
   */
  public void removeLine(CartItem item) {
    items.remove(item);
  }

  public Long getId() {
    return id;
  }

  public String getOwnerId() {
    return ownerId;
  }

  public long getVersion() {
    return version;
  }

  /**
   * Returns the lines in the order they were added.
   *
   * @return an unmodifiable view of the lines
   */
  public List<CartItem> getItems() {
    return Collections.unmodifiableList(items);
  }
}
