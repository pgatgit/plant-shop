package com.plantshop.cart.service;

import static com.plantshop.support.CartFixtures.count;
import static com.plantshop.support.CartFixtures.deleteAllCarts;
import static com.plantshop.support.CartFixtures.productId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.plantshop.cart.domain.Cart;
import com.plantshop.cart.repository.CartRepository;
import com.plantshop.common.error.ApiException;
import com.plantshop.common.error.ProblemCode;
import com.plantshop.support.MySqlTestcontainersConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Concurrent cart mutations on real MySQL (ADR-006): no lost updates, no duplicate carts or lines,
 * and conflicts end either in a successful retry or a 409 {@code concurrent-modification}.
 */
@SpringBootTest
@Import(MySqlTestcontainersConfiguration.class)
@DisplayName("[ADR-006] cart concurrency")
class CartConcurrencyIT {

  private static final int THREADS = 8;

  @Autowired private CartService service;
  @Autowired private CartRepository carts;
  @Autowired private TransactionTemplate transactions;
  @Autowired private JdbcTemplate jdbc;

  private long spider; // stock 50 -> limit 10
  private long fern;

  @BeforeEach
  void setUp() {
    deleteAllCarts(jdbc);
    spider = productId(jdbc, "TRL-SPIDER");
    fern = productId(jdbc, "FRN-BIRDNEST");
  }

  @AfterEach
  void cleanUp() {
    deleteAllCarts(jdbc);
  }

  /** Runs the tasks at the same instant and returns how many succeeded. */
  private int runConcurrently(List<Callable<CartView>> tasks) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<CartView>> futures = new ArrayList<>();
      for (Callable<CartView> task : tasks) {
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  return task.call();
                }));
      }
      start.countDown();
      int successes = 0;
      for (Future<CartView> future : futures) {
        try {
          future.get(30, TimeUnit.SECONDS);
          successes++;
        } catch (java.util.concurrent.ExecutionException e) {
          assertThat(e.getCause())
              .as("only concurrent-modification may fail")
              .isInstanceOfSatisfying(
                  ApiException.class,
                  api -> assertThat(api.code()).isEqualTo(ProblemCode.CONCURRENT_MODIFICATION));
        }
      }
      return successes;
    } finally {
      pool.shutdownNow();
    }
  }

  private int quantity(String owner, long productId) {
    return service.get(owner).lines().stream()
        .filter(line -> line.productId() == productId)
        .mapToInt(CartLineView::quantity)
        .sum();
  }

  @RepeatedTest(3)
  @DisplayName("parallel first adds by one owner create one cart and one line, no lost updates")
  void parallelAddsOfSameProduct_noLostUpdates() throws Exception {
    String owner = "local:race-same";
    List<Callable<CartView>> tasks = new ArrayList<>();
    for (int i = 0; i < THREADS; i++) {
      tasks.add(() -> service.addItem(owner, spider, 1));
    }

    int successes = runConcurrently(tasks);

    assertThat(successes).isPositive();
    assertThat(quantity(owner, spider)).isEqualTo(successes);
    assertThat(count(jdbc, "cart")).isEqualTo(1);
    assertThat(count(jdbc, "cart_item")).isEqualTo(1);
  }

  @RepeatedTest(3)
  @DisplayName("parallel adds of different products keep every successful line")
  void parallelAddsOfDifferentProducts() throws Exception {
    String owner = "local:race-mixed";
    service.addItem(owner, spider, 1);
    List<Long> ids =
        jdbc.queryForList(
            "SELECT id FROM product WHERE stock >= 1 AND sku <> 'TRL-SPIDER' ORDER BY id LIMIT 4",
            Long.class);
    List<Callable<CartView>> tasks = new ArrayList<>();
    for (Long id : ids) {
      tasks.add(() -> service.addItem(owner, id, 1));
    }

    int successes = runConcurrently(tasks);

    assertThat(service.get(owner).lines()).hasSize(1 + successes);
    assertThat(count(jdbc, "cart")).isEqualTo(1);
  }

  @Test
  @DisplayName("concurrent increments never exceed the line limit")
  void parallelIncrements_respectLimit() throws Exception {
    String owner = "local:race-limit";
    service.addItem(owner, fern, 2); // limit 3
    List<Callable<CartView>> tasks = new ArrayList<>();
    for (int i = 0; i < THREADS; i++) {
      tasks.add(
          () -> {
            try {
              return service.addItem(owner, fern, 1);
            } catch (ApiException e) {
              if (e.code() == ProblemCode.QUANTITY_LIMIT_EXCEEDED) {
                return null;
              }
              throw e;
            }
          });
    }

    runConcurrently(tasks);

    assertThat(quantity(owner, fern)).isEqualTo(3);
  }

  @Test
  @DisplayName("a line-only change still conflicts with a concurrent mutation (version bump)")
  void staleCart_lineOnlyChange_isDetected() {
    String owner = "local:stale";
    service.addItem(owner, spider, 1);

    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    status -> {
                      Cart stale = carts.findWithLockByOwnerId(owner).orElseThrow();
                      // Another request changes the cart and commits in the meantime.
                      runInOtherThread(() -> service.updateQuantity(owner, spider, 2));
                      stale.line(spider).orElseThrow().changeQuantity(5);
                      carts.saveAndFlush(stale);
                    }))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);

    assertThat(quantity(owner, spider)).as("the committed change wins").isEqualTo(2);
  }

  private static void runInOtherThread(Runnable action) {
    Thread thread = new Thread(action);
    thread.start();
    try {
      thread.join(TimeUnit.SECONDS.toMillis(30));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
