package com.plantshop.cart.repository;

import com.plantshop.cart.domain.Cart;
import java.util.Optional;

/** Loading a cart for a mutation (custom Spring Data fragment). */
public interface CartLocking {

  /**
   * Loads a cart with its lines and products for a mutation. The cart's version is incremented when
   * the transaction completes, even if only lines changed, so concurrent mutations of the same cart
   * conflict (ADR-006). Only the cart is locked; lines and the read-only products are not.
   *
   * @param ownerId the owner id
   * @return the cart, if the owner has one
   */
  Optional<Cart> findWithLockByOwnerId(String ownerId);
}
