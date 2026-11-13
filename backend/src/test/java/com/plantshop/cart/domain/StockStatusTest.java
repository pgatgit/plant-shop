package com.plantshop.cart.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Tag("BR-12")
class StockStatusTest {

  @ParameterizedTest(name = "quantity {0}, stock {1} -> {2}")
  @CsvSource({
    "1,5,OK",
    "5,5,OK",
    "6,5,INSUFFICIENT",
    "3,1,INSUFFICIENT",
    "1,0,OUT_OF_STOCK",
    "10,0,OUT_OF_STOCK"
  })
  @DisplayName("[BR-12][AC-07.3] stock status compares the line quantity with current stock")
  void of_classifiesLine(int quantity, int stock, StockStatus expected) {
    assertThat(StockStatus.of(quantity, stock)).isEqualTo(expected);
  }
}
