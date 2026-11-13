package com.plantshop.catalog.api;

import static com.plantshop.support.OpenApiContract.conformsToContract;
import static com.plantshop.support.TestAuth.shopper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.plantshop.support.MySqlTestcontainersConfiguration;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Catalog API end to end through the full Spring context on real MySQL; every exchange is checked
 * against the committed OpenAPI contract. (M1: mock principals; real tokens arrive in M2.)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestcontainersConfiguration.class)
class ProductsIT {

  @Autowired private MockMvc mvc;

  private List<String> fetchAllNames(String sort) throws Exception {
    List<String> names = new ArrayList<>();
    for (int page = 0; page < 3; page++) {
      String json =
          mvc.perform(
                  get("/api/v1/products")
                      .param("page", String.valueOf(page))
                      .param("sort", sort)
                      .with(shopper()))
              .andExpect(status().isOk())
              .andExpect(conformsToContract())
              .andReturn()
              .getResponse()
              .getContentAsString();
      names.addAll(JsonPath.read(json, "$.content[*].name"));
    }
    return names;
  }

  @Test
  @Tag("US-03")
  @DisplayName("[AC-03.1][AC-03.3] page 1 has 12 cards, 28 products over 3 pages")
  void firstPage_hasTwelveOfTwentyEight() throws Exception {
    mvc.perform(get("/api/v1/products").with(shopper()))
        .andExpect(status().isOk())
        .andExpect(conformsToContract())
        .andExpect(jsonPath("$.content", hasSize(12)))
        .andExpect(jsonPath("$.totalElements").value(28))
        .andExpect(jsonPath("$.totalPages").value(3))
        .andExpect(jsonPath("$.content[0].name").value("Aloe Vera"))
        .andExpect(jsonPath("$.content[0].price").value("13.99"))
        .andExpect(jsonPath("$.content[0].currency").value("USD"))
        .andExpect(jsonPath("$.content[0].category.code").value("SUCCULENTS"))
        .andExpect(jsonPath("$.content[0].imagePath").value("/images/plants/suc-aloe.svg"));
  }

  @Test
  @Tag("US-03")
  @DisplayName("[AC-03.4] a page beyond the last returns empty content with correct totals")
  void pageBeyondLast_isEmpty() throws Exception {
    mvc.perform(get("/api/v1/products").param("page", "99").with(shopper()))
        .andExpect(status().isOk())
        .andExpect(conformsToContract())
        .andExpect(jsonPath("$.content", hasSize(0)))
        .andExpect(jsonPath("$.totalPages").value(3));
  }

  @Test
  @Tag("US-04")
  @DisplayName("[AC-04.1] name sorts list all 28 products alphabetically across pages")
  void nameSorts_areAlphabeticalAcrossPages() throws Exception {
    List<String> asc = fetchAllNames("name,asc");
    List<String> desc = fetchAllNames("name,desc");

    assertThat(asc).hasSize(28).doesNotHaveDuplicates();
    assertThat(asc).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
    List<String> reversed = new ArrayList<>(asc);
    Collections.reverse(reversed);
    assertThat(desc).containsExactlyElementsOf(reversed);
  }

  @Test
  @Tag("US-04")
  @DisplayName("[AC-04.1][AC-04.3] price sorts are ordered by price with no gaps or overlaps")
  void priceSorts_areOrderedAcrossPages() throws Exception {
    for (String sort : List.of("price,asc", "price,desc")) {
      List<BigDecimal> prices = new ArrayList<>();
      for (int page = 0; page < 3; page++) {
        String json =
            mvc.perform(
                    get("/api/v1/products")
                        .param("page", String.valueOf(page))
                        .param("sort", sort)
                        .with(shopper()))
                .andExpect(conformsToContract())
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<String> raw = JsonPath.read(json, "$.content[*].price");
        raw.forEach(p -> prices.add(new BigDecimal(p)));
      }
      Comparator<BigDecimal> order =
          sort.endsWith("asc") ? Comparator.naturalOrder() : Comparator.reverseOrder();
      assertThat(prices).hasSize(28).isSortedAccordingTo(order);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"mon", "MON", "Mon", "  mon  "})
  @Tag("US-05")
  @DisplayName("[AC-05.1][AC-05.2][AC-05.3] prefix search ignores case and surrounding spaces")
  void search_prefixCaseInsensitive(String q) throws Exception {
    mvc.perform(get("/api/v1/products").param("q", q).with(shopper()))
        .andExpect(status().isOk())
        .andExpect(conformsToContract())
        .andExpect(jsonPath("$.content[*].name", contains("Monstera Deliciosa")))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.query").value(q.strip()));
  }

  @Test
  @Tag("US-05")
  @DisplayName("[AC-05.5] search combines with sort")
  void search_combinesWithSort() throws Exception {
    mvc.perform(get("/api/v1/products").param("q", "ch").with(shopper()))
        .andExpect(status().isOk())
        .andExpect(conformsToContract())
        .andExpect(
            jsonPath("$.content[*].name", contains("Chinese Money Plant", "Christmas Cactus")));
    mvc.perform(
            get("/api/v1/products").param("q", "ch").param("sort", "price,desc").with(shopper()))
        .andExpect(status().isOk())
        .andExpect(conformsToContract())
        .andExpect(
            jsonPath("$.content[*].name", contains("Christmas Cactus", "Chinese Money Plant")));
  }

  @Test
  @Tag("US-05")
  @DisplayName("[BR-04] a one-character search is rejected and conforms to the contract")
  void search_tooShort_isRejected() throws Exception {
    mvc.perform(get("/api/v1/products").param("q", "s").with(shopper()))
        .andExpect(status().isBadRequest())
        .andExpect(conformsToContract())
        .andExpect(jsonPath("$.code").value("invalid-search-term"));
  }

  @Test
  @Tag("US-05")
  @DisplayName("[AC-05.6] no match returns an empty page, not an error")
  void search_noMatch_isEmptyPage() throws Exception {
    mvc.perform(get("/api/v1/products").param("q", "xyz").with(shopper()))
        .andExpect(status().isOk())
        .andExpect(conformsToContract())
        .andExpect(jsonPath("$.content", hasSize(0)))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName("[NFR-SEC-01] the real security chain rejects anonymous catalog access")
  void anonymous_isRejected() throws Exception {
    mvc.perform(get("/api/v1/products"))
        .andExpect(status().isUnauthorized())
        .andExpect(conformsToContract())
        .andExpect(jsonPath("$.code").value("unauthenticated"));
  }

  @Test
  @DisplayName("unknown API paths are not leaked: 401 before authentication")
  void unknownPath_anonymous_is401() throws Exception {
    mvc.perform(get("/api/v1/nope")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/nope").with(shopper()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("not-found"));
  }
}
