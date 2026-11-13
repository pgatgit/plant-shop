package com.plantshop.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Sort;

@Tag("US-04")
class ProductSortTest {

  @Test
  @DisplayName("[BR-03] each whitelisted value parses to its ordering")
  void fromParameter_whitelistedValues_parse() {
    assertThat(ProductSort.fromParameter("name,asc")).contains(ProductSort.NAME_ASC);
    assertThat(ProductSort.fromParameter("name,desc")).contains(ProductSort.NAME_DESC);
    assertThat(ProductSort.fromParameter("price,asc")).contains(ProductSort.PRICE_ASC);
    assertThat(ProductSort.fromParameter("price,desc")).contains(ProductSort.PRICE_DESC);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "name", "NAME,ASC", "price,up", "stock,asc", "name,asc;drop"})
  @DisplayName("[BR-03] anything else is rejected")
  void fromParameter_unknownValues_areEmpty(String value) {
    assertThat(ProductSort.fromParameter(value)).isEmpty();
  }

  @Test
  @DisplayName("[AC-04.3] price orderings break ties by name then id")
  void priceSorts_haveDeterministicTieBreakers() {
    assertThat(ProductSort.PRICE_DESC.toSort())
        .containsExactly(Sort.Order.desc("price"), Sort.Order.asc("name"), Sort.Order.asc("id"));
    assertThat(ProductSort.PRICE_ASC.toSort())
        .containsExactly(Sort.Order.asc("price"), Sort.Order.asc("name"), Sort.Order.asc("id"));
  }

  @Test
  void nameSorts_breakTiesById_andDefaultIsNameAscending() {
    assertThat(ProductSort.NAME_DESC.toSort())
        .containsExactly(Sort.Order.desc("name"), Sort.Order.asc("id"));
    assertThat(ProductSort.DEFAULT).isEqualTo(ProductSort.NAME_ASC);
    assertThat(ProductSort.NAME_ASC.parameter()).isEqualTo("name,asc");
  }
}
