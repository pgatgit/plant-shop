package com.plantshop.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantshop.support.MySqlTestcontainersConfiguration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * NFR-PERF-04: the prefix search must be an index range scan. With 28 rows the optimizer may
 * rightly prefer a table scan, so 5,000 synthetic rows are added first (06 §3).
 */
@SpringBootTest
@Import(MySqlTestcontainersConfiguration.class)
@Tag("US-05")
class SearchExplainIT {

  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void addBulkRows() {
    jdbc.execute("SET SESSION cte_max_recursion_depth = 10000");
    jdbc.update(
        "INSERT INTO product (sku, name, description, price, stock, light, watering, pet_safe,"
            + " image_path, category_id)"
            + " WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 5000)"
            + " SELECT CONCAT('BULK-', n), CONCAT('Bulk ', LPAD(n, 5, '0'), ' ', MD5(n)),"
            + " 'Synthetic row.', 10.00, 1, 'LOW', 'LOW', TRUE, '/x.svg',"
            + " (SELECT id FROM category WHERE code = 'FERNS') FROM seq");
    jdbc.execute("ANALYZE TABLE product");
  }

  @AfterEach
  void removeBulkRows() {
    jdbc.update("DELETE FROM product WHERE sku LIKE 'BULK-%'");
  }

  @Test
  @DisplayName("[NFR-PERF-04] name LIKE 'term%' uses a range scan on ix_product_name")
  void prefixSearch_usesNameIndexRangeScan() {
    Map<String, Object> plan =
        jdbc.queryForMap(
            "EXPLAIN SELECT p.id FROM product p WHERE p.name LIKE ? ESCAPE '!'"
                + " ORDER BY p.name, p.id LIMIT 12",
            "Mon%");

    assertThat(plan.get("key")).isEqualTo("ix_product_name");
    assertThat(plan.get("type")).isEqualTo("range");
  }

  @Test
  @DisplayName("case and accent variants hit the same index (no function on the column)")
  void caseVariant_usesSameIndex() {
    Map<String, Object> plan =
        jdbc.queryForMap(
            "EXPLAIN SELECT p.id FROM product p WHERE p.name LIKE ? ESCAPE '!'"
                + " ORDER BY p.name, p.id LIMIT 12",
            "BÜLK 0001%");

    assertThat(plan.get("key")).isEqualTo("ix_product_name");
    assertThat(plan.get("type")).isEqualTo("range");
  }
}
