package com.plantshop.common.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantshop.support.MySqlTestcontainersConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** Flyway applies the full migration set to an empty MySQL and the seed is complete. */
@SpringBootTest
@Import(MySqlTestcontainersConfiguration.class)
class FlywayMigrationIT {

  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("[NFR-REL-04] V1–V5 are applied successfully, in order, and nothing else")
  void migrations_applyInOrder() {
    List<String> versions =
        jdbc.queryForList(
            "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank",
            String.class);

    assertThat(versions).containsExactly("1", "2", "3", "4", "5");
  }

  @Test
  @DisplayName("[06 §5] seed contains 6 categories and 28 products with unique SKUs and names")
  void seed_isComplete() {
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM category", Integer.class)).isEqualTo(6);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product", Integer.class)).isEqualTo(28);
    assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT name) FROM product", Integer.class))
        .isEqualTo(28);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM product WHERE CHAR_LENGTH(description) NOT BETWEEN 150 AND"
                    + " 300",
                Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM product WHERE image_path <> CONCAT('/images/plants/',"
                    + " LOWER(sku), '.svg')",
                Integer.class))
        .isZero();
  }

  @Test
  @DisplayName("[06 §5] the seed covers out-of-stock and low-stock cases used by tests")
  void seed_hasStockEdgeCases() {
    assertThat(
            jdbc.queryForList("SELECT sku FROM product WHERE stock = 0 ORDER BY sku", String.class))
        .containsExactly("FLW-BROMELIAD", "FRN-MAIDENHAIR");
    assertThat(
            jdbc.queryForObject(
                "SELECT stock FROM product WHERE sku = 'FRN-BIRDNEST'", Integer.class))
        .isEqualTo(3);
  }

  @Test
  @DisplayName("[06 §2] collations: case/accent-insensitive names, binary identifiers")
  void columns_useExpectedCollations() {
    List<Map<String, Object>> columns =
        jdbc.queryForList(
            "SELECT table_name AS t, column_name AS c, collation_name AS coll"
                + " FROM information_schema.columns WHERE table_schema = DATABASE()"
                + " AND column_name IN ('name', 'sku', 'code', 'owner_id')");

    assertThat(columns)
        .extracting(m -> m.get("t") + "." + m.get("c") + "=" + m.get("coll"))
        .contains(
            "product.name=utf8mb4_0900_ai_ci",
            "category.name=utf8mb4_0900_ai_ci",
            "product.sku=utf8mb4_bin",
            "category.code=utf8mb4_bin",
            "cart.owner_id=utf8mb4_bin");
  }
}
