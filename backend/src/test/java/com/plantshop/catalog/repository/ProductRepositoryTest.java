package com.plantshop.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.plantshop.catalog.domain.Product;
import com.plantshop.catalog.service.ProductSort;
import com.plantshop.support.MySqlTestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

/** Repository behaviour on real MySQL with the Flyway schema and seed (ADR-005, ADR-011). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MySqlTestcontainersConfiguration.class)
class ProductRepositoryTest {

  @Autowired private ProductRepository repository;
  @Autowired private JdbcTemplate jdbc;

  private List<String> names(Page<Product> page) {
    return page.getContent().stream().map(Product::getName).toList();
  }

  /** Inserts a catalog row through bound parameters (rolled back with the test transaction). */
  private void insertFixture(String sku, String name) {
    jdbc.update(
        "INSERT INTO product (sku, name, description, price, stock, light, watering, pet_safe,"
            + " image_path, category_id) VALUES (?, ?, 'Fixture.', 1.00, 1, 'LOW', 'LOW', TRUE,"
            + " '/x.svg', (SELECT id FROM category WHERE code = 'FERNS'))",
        sku,
        name);
  }

  private Page<Product> search(String prefix) {
    return repository.findByNameStartingWith(
        prefix, PageRequest.of(0, 48, ProductSort.NAME_ASC.toSort()));
  }

  @Test
  @Tag("US-05")
  @DisplayName("[AC-05.1] search is prefix-only: 'mon' finds Monstera, not Swiss Cheese Monstera")
  void search_isPrefixOnly() {
    assertThat(names(search("mon"))).containsExactly("Monstera Deliciosa");
  }

  @Test
  @Tag("US-05")
  @DisplayName("[AC-05.2] search ignores case")
  void search_ignoresCase() {
    assertThat(names(search("MON"))).containsExactly("Monstera Deliciosa");
    assertThat(names(search("mOnStErA"))).containsExactly("Monstera Deliciosa");
    assertThat(names(search("peace li"))).containsExactly("Peace Lily");
  }

  @Test
  @Tag("US-05")
  @DisplayName("[AC-05.2] search ignores accents in both directions")
  void search_ignoresAccents() {
    insertFixture("T-CREME", "Crème Brûlée Test");
    insertFixture("T-ELAN", "Élan Test");

    assertThat(names(search("creme"))).containsExactly("Crème Brûlée Test");
    assertThat(names(search("CRÉME BRU"))).containsExactly("Crème Brûlée Test");
    assertThat(names(search("elan"))).containsExactly("Élan Test");
  }

  @Test
  @Tag("US-05")
  @Sql(
      statements =
          "INSERT INTO product (sku, name, description, price, stock, light, watering,"
              + " pet_safe, image_path, category_id) VALUES ('T-PCT', '100% Organic Test', 'Fixture.',"
              + " 1.00, 1, 'LOW', 'LOW', TRUE, '/x.svg', (SELECT id FROM category WHERE code = 'FERNS')),"
              + " ('T-UND', 'A_B Test', 'Fixture.', 1.00, 1, 'LOW', 'LOW', TRUE, '/x.svg',"
              + " (SELECT id FROM category WHERE code = 'FERNS'))")
  @DisplayName("[AC-05.7] %, _ and backslash are matched literally")
  void search_treatsWildcardsLiterally() {
    assertThat(names(search("%"))).isEmpty();
    assertThat(names(search("%%"))).isEmpty();
    assertThat(names(search("__"))).isEmpty();
    assertThat(names(search("\\%"))).isEmpty();
    assertThat(names(search("100%"))).containsExactly("100% Organic Test");
    assertThat(names(search("A_"))).containsExactly("A_B Test");
    assertThat(names(search("Bird's"))).containsExactly("Bird's Nest Fern");
  }

  @Test
  @Tag("US-03")
  @DisplayName("[AC-03.3] 28 seeded products make 3 pages of 12, 12 and 4")
  void findAll_pagesSeedCatalog() {
    Page<Product> first = repository.findAll(PageRequest.of(0, 12, ProductSort.NAME_ASC.toSort()));
    Page<Product> last = repository.findAll(PageRequest.of(2, 12, ProductSort.NAME_ASC.toSort()));

    assertThat(first.getTotalElements()).isEqualTo(28);
    assertThat(first.getTotalPages()).isEqualTo(3);
    assertThat(first.getContent()).hasSize(12);
    assertThat(last.getContent()).hasSize(4);
    assertThat(first.getContent().get(0).getName()).isEqualTo("Aloe Vera");
  }

  @Test
  @Tag("US-04")
  @Sql(
      statements =
          "INSERT INTO product (sku, name, description, price, stock, light, watering,"
              + " pet_safe, image_path, category_id) VALUES ('T-TIE', 'Aaa Tie Test', 'Fixture.', 22.99,"
              + " 1, 'LOW', 'LOW', TRUE, '/x.svg', (SELECT id FROM category WHERE code = 'FERNS'))")
  @DisplayName("[AC-04.3] equal prices are ordered by name, also in descending price order")
  void priceSort_breaksTiesByName() {
    List<String> desc =
        names(repository.findAll(PageRequest.of(0, 48, ProductSort.PRICE_DESC.toSort())));
    List<String> asc =
        names(repository.findAll(PageRequest.of(0, 48, ProductSort.PRICE_ASC.toSort())));

    assertThat(desc).containsSubsequence("Aaa Tie Test", "Boston Fern");
    assertThat(asc).containsSubsequence("Aaa Tie Test", "Boston Fern");
    assertThat(desc.get(0)).isEqualTo("Fiddle Leaf Fig");
    assertThat(asc.get(0)).isEqualTo("Echeveria 'Lola'");
  }

  @Test
  @DisplayName("listing fetches categories with the products (no N+1)")
  void findAll_loadsCategoryEagerly() {
    Page<Product> page = repository.findAll(PageRequest.of(0, 12, ProductSort.NAME_ASC.toSort()));

    assertThat(page.getContent())
        .allSatisfy(
            p ->
                assertThat(org.hibernate.Hibernate.isInitialized(p.getCategory()))
                    .as("category of %s initialised", p.getName())
                    .isTrue());
  }

  @Test
  @DisplayName("[06 §3] CHECK constraints reject invalid prices, stock and enum values")
  void checkConstraints_rejectInvalidRows() {
    String insert =
        "INSERT INTO product (sku, name, description, price, stock, light, watering, pet_safe,"
            + " image_path, category_id) VALUES (?, 'Bad', 'x', ?, ?, ?, 'LOW', TRUE, '/x.svg',"
            + " (SELECT id FROM category WHERE code = 'FERNS'))";

    // MySQL reports CHECK violations as error 3819, which Spring does not categorise further.
    assertThatThrownBy(() -> jdbc.update(insert, "T-B1", 0, 1, "LOW"))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("ck_product_price");
    assertThatThrownBy(() -> jdbc.update(insert, "T-B2", 1, -1, "LOW"))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("ck_product_stock");
    assertThatThrownBy(() -> jdbc.update(insert, "T-B3", 1, 1, "SHADE"))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("ck_product_light");
    assertThatThrownBy(() -> jdbc.update(insert, "FOL-MONSTERA", 1, 1, "LOW"))
        .as("duplicate sku")
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
