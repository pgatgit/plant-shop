package com.plantshop.cart.service;

import com.plantshop.cart.domain.Cart;
import com.plantshop.cart.domain.CartItem;
import com.plantshop.cart.domain.LineLimit;
import com.plantshop.cart.repository.CartRepository;
import com.plantshop.catalog.domain.Product;
import com.plantshop.catalog.service.ProductService;
import com.plantshop.common.error.ApiException;
import com.plantshop.common.error.ProblemCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Cart use cases (US-06…US-09, ADR-006). Every method works on the cart of the given owner only;
 * owner ids never come from the request (BR-13).
 *
 * <p>Mutations run in their own transaction and bump the cart version. A conflicting concurrent
 * mutation (optimistic lock failure, deadlock, or a racing insert of the same cart or line) is
 * retried once in a fresh transaction; a second conflict is reported as 409 {@code
 * concurrent-modification}. Each mutation increments {@code plantshop.cart.mutations{op,outcome}}
 * (NFR-OBS-04).
 */
@Service
public class CartService {

  private static final Logger LOG = LoggerFactory.getLogger(CartService.class);
  private static final String METRIC = "plantshop.cart.mutations";
  private static final Set<String> RACE_CONSTRAINTS =
      Set.of("uk_cart_owner", "uk_cart_item_cart_product");

  private final CartRepository carts;
  private final ProductService products;
  private final TransactionOperations transactions;
  private final MeterRegistry meters;

  /**
   * Creates the service.
   *
   * @param carts cart repository
   * @param products catalog lookups
   * @param transactions runs each attempt in its own transaction
   * @param meters metrics registry
   */
  public CartService(
      CartRepository carts,
      ProductService products,
      TransactionOperations transactions,
      MeterRegistry meters) {
    this.carts = carts;
    this.products = products;
    this.transactions = transactions;
    this.meters = meters;
  }

  /**
   * Returns the owner's cart; an empty view if they have none (no cart is created on read).
   *
   * @param ownerId the owner
   * @return the cart view
   */
  public CartView get(String ownerId) {
    return transactions.execute(
        status -> carts.findByOwnerId(ownerId).map(CartView::of).orElseGet(CartView::empty));
  }

  /**
   * Adds a product, creating the cart or incrementing an existing line (BR-06, BR-07).
   *
   * @param ownerId the owner
   * @param productId the product
   * @param quantity how many to add (1–10)
   * @return the updated cart
   * @throws ApiException {@code product-not-found}, {@code out-of-stock}, {@code
   *     quantity-limit-exceeded} or {@code concurrent-modification}
   */
  public CartView addItem(String ownerId, long productId, int quantity) {
    return mutate("add", () -> doAdd(ownerId, productId, quantity));
  }

  /**
   * Sets a line's quantity. Decreases are always allowed; increases are checked against the limit.
   *
   * @param ownerId the owner
   * @param productId the product of the line
   * @param quantity the new quantity (1–10)
   * @return the updated cart
   * @throws ApiException {@code cart-item-not-found}, {@code out-of-stock}, {@code
   *     quantity-limit-exceeded} or {@code concurrent-modification}
   */
  public CartView updateQuantity(String ownerId, long productId, int quantity) {
    return mutate("update", () -> doUpdate(ownerId, productId, quantity));
  }

  /**
   * Removes a line.
   *
   * @param ownerId the owner
   * @param productId the product of the line
   * @return the updated cart
   * @throws ApiException {@code cart-item-not-found} or {@code concurrent-modification}
   */
  public CartView removeItem(String ownerId, long productId) {
    return mutate("remove", () -> doRemove(ownerId, productId));
  }

  private CartView doAdd(String ownerId, long productId, int quantity) {
    Product product =
        products
            .findById(productId)
            .orElseThrow(
                () ->
                    new ApiException(
                        ProblemCode.PRODUCT_NOT_FOUND,
                        "This plant is no longer available.",
                        Map.of("productId", productId)));
    Cart cart = carts.findWithLockByOwnerId(ownerId).orElseGet(() -> Cart.forOwner(ownerId));
    CartItem line = cart.line(productId).orElse(null);
    int requested = (line == null ? 0 : line.getQuantity()) + quantity;
    checkLimit(product, requested);
    if (line == null) {
      cart.addLine(product, quantity);
    } else {
      line.changeQuantity(requested);
    }
    return CartView.of(carts.saveAndFlush(cart));
  }

  private CartView doUpdate(String ownerId, long productId, int quantity) {
    Cart cart = lockedCart(ownerId, productId);
    CartItem line = existingLine(cart, productId);
    if (quantity > line.getQuantity()) {
      checkLimit(line.getProduct(), quantity);
    }
    line.changeQuantity(quantity);
    return CartView.of(carts.saveAndFlush(cart));
  }

  private CartView doRemove(String ownerId, long productId) {
    Cart cart = lockedCart(ownerId, productId);
    cart.removeLine(existingLine(cart, productId));
    return CartView.of(carts.saveAndFlush(cart));
  }

  private Cart lockedCart(String ownerId, long productId) {
    return carts.findWithLockByOwnerId(ownerId).orElseThrow(() -> lineNotFound(productId));
  }

  private static CartItem existingLine(Cart cart, long productId) {
    return cart.line(productId).orElseThrow(() -> lineNotFound(productId));
  }

  private static ApiException lineNotFound(long productId) {
    return new ApiException(
        ProblemCode.CART_ITEM_NOT_FOUND,
        "This plant is not in your cart.",
        Map.of("productId", productId));
  }

  private static void checkLimit(Product product, int requested) {
    LineLimit limit = LineLimit.of(product.getStock());
    if (limit.max() == 0) {
      throw new ApiException(
          ProblemCode.OUT_OF_STOCK,
          product.getName() + " is out of stock.",
          Map.of("productId", product.getId()));
    }
    if (!limit.allows(requested)) {
      throw new ApiException(
          ProblemCode.QUANTITY_LIMIT_EXCEEDED,
          "You can have at most " + limit.max() + " of " + product.getName() + " in your cart.",
          Map.of("limit", limit.max(), "requested", requested, "productId", product.getId()));
    }
  }

  private CartView mutate(String op, Supplier<CartView> action) {
    try {
      CartView view = withOneRetry(op, action);
      count(op, "success");
      return view;
    } catch (ApiException e) {
      count(op, e.code().slug());
      throw e;
    } catch (RuntimeException e) {
      count(op, "error");
      throw e;
    }
  }

  private CartView withOneRetry(String op, Supplier<CartView> action) {
    try {
      return transactions.execute(status -> action.get());
    } catch (RuntimeException first) {
      if (!isWriteConflict(first)) {
        throw first;
      }
      LOG.info("Concurrent cart {} detected, retrying once: {}", op, first.getClass().getName());
    }
    try {
      return transactions.execute(status -> action.get());
    } catch (RuntimeException second) {
      if (!isWriteConflict(second)) {
        throw second;
      }
      LOG.info("Concurrent cart {} failed again: {}", op, second.getClass().getName());
      throw new ApiException(
          ProblemCode.CONCURRENT_MODIFICATION,
          "Your cart was changed by another request at the same time. Please try again.");
    }
  }

  /**
   * Conflicts caused by a concurrent request on the same cart: optimistic lock and deadlock
   * failures, and duplicate-key errors from two requests creating the same cart or line.
   */
  static boolean isWriteConflict(RuntimeException e) {
    if (e instanceof ConcurrencyFailureException || e instanceof DuplicateKeyException) {
      return true;
    }
    if (!(e instanceof DataIntegrityViolationException)) {
      return false;
    }
    for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException violation
          && violation.getConstraintName() != null) {
        String name = violation.getConstraintName();
        return RACE_CONSTRAINTS.stream().anyMatch(name::endsWith);
      }
    }
    return false;
  }

  private void count(String op, String outcome) {
    Counter.builder(METRIC)
        .description("Cart mutations by operation and outcome")
        .tag("op", op)
        .tag("outcome", outcome)
        .register(meters)
        .increment();
  }
}
