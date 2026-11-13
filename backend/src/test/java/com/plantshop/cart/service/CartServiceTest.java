package com.plantshop.cart.service;

import static com.plantshop.support.TestProducts.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plantshop.cart.domain.Cart;
import com.plantshop.cart.repository.CartRepository;
import com.plantshop.catalog.domain.Product;
import com.plantshop.catalog.service.ProductService;
import com.plantshop.common.error.ApiException;
import com.plantshop.common.error.ProblemCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.SQLException;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionOperations;

class CartServiceTest {

  private static final String OWNER = "local:shopper";

  private final CartRepository carts = mock(CartRepository.class);
  private final ProductService products = mock(ProductService.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final CartService service =
      new CartService(carts, products, TransactionOperations.withoutTransaction(), meters);

  private final Product fern = product(11, "Bird's Nest Fern", "26.99", 3);
  private final Product maidenhair = product(12, "Maidenhair Fern", "19.99", 0);
  private final Product spider = product(13, "Spider Plant", "12.99", 50);

  @BeforeEach
  void setUp() {
    when(products.findById(11L)).thenReturn(Optional.of(fern));
    when(products.findById(12L)).thenReturn(Optional.of(maidenhair));
    when(products.findById(13L)).thenReturn(Optional.of(spider));
    when(products.findById(99L)).thenReturn(Optional.empty());
    when(carts.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  private Cart existingCart() {
    Cart cart = Cart.forOwner(OWNER);
    when(carts.findWithLockByOwnerId(OWNER)).thenReturn(Optional.of(cart));
    when(carts.findByOwnerId(OWNER)).thenReturn(Optional.of(cart));
    return cart;
  }

  /** Like the database after a rollback: every attempt loads a fresh, empty cart. */
  private void freshCartPerAttempt() {
    when(carts.findWithLockByOwnerId(OWNER)).thenAnswer(inv -> Optional.of(Cart.forOwner(OWNER)));
  }

  private double mutations(String op, String outcome) {
    var counter =
        meters.find("plantshop.cart.mutations").tag("op", op).tag("outcome", outcome).counter();
    return counter == null ? 0 : counter.count();
  }

  private static ApiException apiError(Runnable call) {
    try {
      call.run();
    } catch (ApiException e) {
      return e;
    }
    throw new AssertionError("expected ApiException");
  }

  @Nested
  @Tag("US-07")
  class Get {

    @Test
    @DisplayName("[BR-06] reading without a cart returns an empty view and creates nothing")
    void noCart_isEmptyAndNotCreated() {
      when(carts.findByOwnerId(OWNER)).thenReturn(Optional.empty());

      CartView view = service.get(OWNER);

      assertThat(view.lines()).isEmpty();
      verify(carts, never()).saveAndFlush(any());
    }

    @Test
    void existingCart_isReturned() {
      existingCart().addLine(fern, 2);

      assertThat(service.get(OWNER).itemCount()).isEqualTo(2);
    }
  }

  @Nested
  @Tag("US-06")
  class Add {

    @Test
    @DisplayName("[AC-06.1][BR-06] first add creates the cart lazily with one line")
    void firstAdd_createsCart() {
      when(carts.findWithLockByOwnerId(OWNER)).thenReturn(Optional.empty());

      CartView view = service.addItem(OWNER, 11L, 1);

      assertThat(view.lines())
          .singleElement()
          .satisfies(
              l -> {
                assertThat(l.productId()).isEqualTo(11L);
                assertThat(l.quantity()).isEqualTo(1);
              });
      verify(carts).saveAndFlush(any(Cart.class));
      assertThat(mutations("add", "success")).isEqualTo(1);
    }

    @Test
    @DisplayName("[AC-06.2][BR-07] re-adding increments the existing line")
    void reAdd_increments() {
      existingCart().addLine(spider, 2);

      CartView view = service.addItem(OWNER, 13L, 3);

      assertThat(view.lines()).singleElement().extracting(CartLineView::quantity).isEqualTo(5);
    }

    @Test
    @DisplayName("[AC-06.3][BR-08] exceeding min(stock,10) is 409 and leaves the cart unchanged")
    void overLimit_isRejected() {
      Cart cart = existingCart();
      cart.addLine(fern, 3);

      ApiException error = apiError(() -> service.addItem(OWNER, 11L, 1));

      assertThat(error.code()).isEqualTo(ProblemCode.QUANTITY_LIMIT_EXCEEDED);
      assertThat(error.getMessage())
          .isEqualTo("You can have at most 3 of Bird's Nest Fern in your cart.");
      assertThat(error.properties())
          .containsEntry("limit", 3)
          .containsEntry("requested", 4)
          .containsEntry("productId", 11L);
      assertThat(cart.line(11L).orElseThrow().getQuantity()).isEqualTo(3);
      verify(carts, never()).saveAndFlush(any());
      assertThat(mutations("add", "quantity-limit-exceeded")).isEqualTo(1);
    }

    @Test
    @DisplayName("[BR-08] the hard cap is 10 even with plenty of stock")
    void overTen_isRejected() {
      existingCart().addLine(spider, 8);

      assertThat(apiError(() -> service.addItem(OWNER, 13L, 3)).properties())
          .containsEntry("limit", 10)
          .containsEntry("requested", 11);
    }

    @Test
    @DisplayName("[AC-06.4][BR-08] out-of-stock products cannot be added")
    void outOfStock_isRejected() {
      existingCart();

      ApiException error = apiError(() -> service.addItem(OWNER, 12L, 1));

      assertThat(error.code()).isEqualTo(ProblemCode.OUT_OF_STOCK);
      assertThat(error.getMessage()).isEqualTo("Maidenhair Fern is out of stock.");
      assertThat(error.properties()).containsEntry("productId", 12L);
    }

    @Test
    @DisplayName("[AC-06.6] unknown product is 404 product-not-found")
    void unknownProduct_isNotFound() {
      ApiException error = apiError(() -> service.addItem(OWNER, 99L, 1));

      assertThat(error.code()).isEqualTo(ProblemCode.PRODUCT_NOT_FOUND);
      assertThat(error.getMessage()).isEqualTo("This plant is no longer available.");
      assertThat(mutations("add", "product-not-found")).isEqualTo(1);
    }
  }

  @Nested
  @Tag("US-08")
  class Update {

    @Test
    @DisplayName("[AC-08.1] quantity can be set within the limit")
    void withinLimit_isApplied() {
      existingCart().addLine(spider, 1);

      assertThat(service.updateQuantity(OWNER, 13L, 7).itemCount()).isEqualTo(7);
      assertThat(mutations("update", "success")).isEqualTo(1);
    }

    @Test
    @DisplayName("[AC-08.3][BR-08] increasing beyond the limit is 409")
    void aboveLimit_isRejected() {
      existingCart().addLine(fern, 1);

      ApiException error = apiError(() -> service.updateQuantity(OWNER, 11L, 4));

      assertThat(error.code()).isEqualTo(ProblemCode.QUANTITY_LIMIT_EXCEEDED);
      assertThat(error.properties()).containsEntry("limit", 3).containsEntry("requested", 4);
    }

    @Test
    @DisplayName("[AC-07.3] decreasing is always allowed, even when still above current stock")
    void decrease_isAllowedEvenIfStillAboveStock() {
      existingCart().addLine(fern, 3);
      org.springframework.test.util.ReflectionTestUtils.setField(fern, "stock", 1);

      assertThat(service.updateQuantity(OWNER, 11L, 2).lines().get(0).quantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("[BR-08] increasing a line whose product sold out is 409 out-of-stock")
    void increase_whenSoldOut_isOutOfStock() {
      Cart cart = existingCart();
      cart.addLine(fern, 1);
      org.springframework.test.util.ReflectionTestUtils.setField(fern, "stock", 0);

      assertThat(apiError(() -> service.updateQuantity(OWNER, 11L, 2)).code())
          .isEqualTo(ProblemCode.OUT_OF_STOCK);
    }

    @Test
    @DisplayName("product not in the cart is 404 cart-item-not-found")
    void missingLine_isNotFound() {
      existingCart();

      ApiException error = apiError(() -> service.updateQuantity(OWNER, 11L, 1));

      assertThat(error.code()).isEqualTo(ProblemCode.CART_ITEM_NOT_FOUND);
      assertThat(error.properties()).containsEntry("productId", 11L);
    }

    @Test
    void noCartAtAll_isNotFound() {
      when(carts.findWithLockByOwnerId(OWNER)).thenReturn(Optional.empty());

      assertThat(apiError(() -> service.updateQuantity(OWNER, 11L, 1)).code())
          .isEqualTo(ProblemCode.CART_ITEM_NOT_FOUND);
    }
  }

  @Nested
  @Tag("US-09")
  class Remove {

    @Test
    @DisplayName("[AC-09.1] removing drops the line")
    void remove_dropsLine() {
      Cart cart = existingCart();
      cart.addLine(fern, 1);
      cart.addLine(spider, 2);

      CartView view = service.removeItem(OWNER, 11L);

      assertThat(view.lines()).extracting(CartLineView::productId).containsExactly(13L);
      assertThat(mutations("remove", "success")).isEqualTo(1);
    }

    @Test
    @DisplayName("[AC-09.3] removing a product not in the cart is 404")
    void remove_missing_isNotFound() {
      existingCart();

      assertThat(apiError(() -> service.removeItem(OWNER, 11L)).code())
          .isEqualTo(ProblemCode.CART_ITEM_NOT_FOUND);
      assertThat(mutations("remove", "cart-item-not-found")).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("[ADR-006] optimistic locking with one retry")
  class Retry {

    @Test
    void optimisticConflict_isRetriedOnce() {
      freshCartPerAttempt();
      when(carts.saveAndFlush(any()))
          .thenThrow(new ObjectOptimisticLockingFailureException(Cart.class, 1L))
          .thenAnswer(inv -> inv.getArgument(0));

      assertThat(service.addItem(OWNER, 13L, 1).itemCount()).isEqualTo(1);
      verify(carts, times(2)).saveAndFlush(any());
      assertThat(mutations("add", "success")).isEqualTo(1);
    }

    @Test
    void concurrentCartCreation_duplicateKey_isRetried() {
      when(carts.findWithLockByOwnerId(OWNER)).thenReturn(Optional.empty());
      when(carts.saveAndFlush(any()))
          .thenThrow(duplicate("uk_cart_owner"))
          .thenAnswer(inv -> inv.getArgument(0));

      assertThat(service.addItem(OWNER, 13L, 1).itemCount()).isEqualTo(1);
      verify(carts, times(2)).saveAndFlush(any());
    }

    @Test
    void deadlock_isRetried() {
      freshCartPerAttempt();
      when(carts.saveAndFlush(any()))
          .thenThrow(new CannotAcquireLockException("deadlock"))
          .thenAnswer(inv -> inv.getArgument(0));

      assertThat(service.addItem(OWNER, 13L, 1).itemCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a second conflict is 409 concurrent-modification")
    void secondConflict_isConcurrentModification() {
      freshCartPerAttempt();
      when(carts.saveAndFlush(any()))
          .thenThrow(new ObjectOptimisticLockingFailureException(Cart.class, 1L));

      ApiException error = apiError(() -> service.addItem(OWNER, 13L, 1));

      assertThat(error.code()).isEqualTo(ProblemCode.CONCURRENT_MODIFICATION);
      verify(carts, times(2)).saveAndFlush(any());
      assertThat(mutations("add", "concurrent-modification")).isEqualTo(1);
    }

    @Test
    void otherIntegrityViolations_areNotRetried() {
      freshCartPerAttempt();
      DataIntegrityViolationException check = duplicate("ck_cart_item_quantity");
      when(carts.saveAndFlush(any())).thenThrow(check);

      assertThatThrownBy(() -> service.addItem(OWNER, 13L, 1)).isSameAs(check);
      verify(carts, times(1)).saveAndFlush(any());
      assertThat(mutations("add", "error")).isEqualTo(1);
    }

    private DataIntegrityViolationException duplicate(String constraint) {
      return new DataIntegrityViolationException(
          "could not execute statement",
          new ConstraintViolationException(
              "Duplicate entry", new SQLException("Duplicate entry", "23000", 1062), constraint));
    }
  }
}
