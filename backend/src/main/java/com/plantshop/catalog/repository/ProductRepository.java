package com.plantshop.catalog.repository;

import com.plantshop.catalog.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Product queries. Case- and accent-insensitivity come from the column collation ({@code
 * utf8mb4_0900_ai_ci}); never use {@code IgnoreCase} here, it wraps the column in {@code UPPER()}
 * and defeats {@code ix_product_name} (ADR-005).
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

  @Override
  @EntityGraph(attributePaths = "category")
  Page<Product> findAll(Pageable pageable);

  /**
   * Prefix search on the product name. Spring Data escapes {@code %}, {@code _} and {@code \} in
   * the argument, so they match literally (AC-05.7).
   */
  @EntityGraph(attributePaths = "category")
  Page<Product> findByNameStartingWith(String prefix, Pageable pageable);
}
