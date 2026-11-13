package com.plantshop.cart.repository;

import com.plantshop.cart.domain.Cart;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Optional;

/** Implementation of {@link CartLocking}, picked up by Spring Data through its name. */
class CartLockingImpl implements CartLocking {

  private final EntityManager entityManager;

  CartLockingImpl(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public Optional<Cart> findWithLockByOwnerId(String ownerId) {
    Optional<Cart> cart =
        entityManager
            .createQuery(
                "select distinct c from Cart c"
                    + " left join fetch c.items i"
                    + " left join fetch i.product"
                    + " where c.ownerId = :ownerId",
                Cart.class)
            .setParameter("ownerId", ownerId)
            .getResultStream()
            .findFirst();
    cart.ifPresent(c -> entityManager.lock(c, LockModeType.OPTIMISTIC_FORCE_INCREMENT));
    return cart;
  }
}
