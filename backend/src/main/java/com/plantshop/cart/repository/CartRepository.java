package com.plantshop.cart.repository;

import com.plantshop.cart.domain.Cart;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Carts are always loaded with their lines and products in one query (no N+1). */
public interface CartRepository extends JpaRepository<Cart, Long>, CartLocking {

  /**
   * Loads a cart for reading.
   *
   * @param ownerId the owner id
   * @return the cart, if the owner has one
   */
  @EntityGraph(attributePaths = {"items", "items.product"})
  Optional<Cart> findByOwnerId(String ownerId);
}
