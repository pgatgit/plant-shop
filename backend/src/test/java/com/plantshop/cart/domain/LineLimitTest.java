package com.plantshop.cart.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Tag("BR-08")
class LineLimitTest {

  @ParameterizedTest(name = "stock {0} -> at most {1}")
  @CsvSource({"0,0", "1,1", "3,3", "9,9", "10,10", "11,10", "50,10"})
  @DisplayName("[BR-08] the limit is min(stock, 10)")
  void of_isMinOfStockAndTen(int stock, int expected) {
    assertThat(LineLimit.of(stock).max()).isEqualTo(expected);
  }

  @ParameterizedTest(name = "limit {0}, quantity {1} -> {2}")
  @CsvSource({"3,1,true", "3,3,true", "3,4,false", "3,0,false", "0,1,false", "10,10,true"})
  void allows_quantitiesBetweenOneAndTheLimit(int stock, int quantity, boolean allowed) {
    assertThat(LineLimit.of(stock).allows(quantity)).isEqualTo(allowed);
  }

  @ParameterizedTest
  @CsvSource({"-1,0"})
  void negativeStock_isTreatedAsNone(int stock, int expected) {
    assertThat(LineLimit.of(stock).max()).isEqualTo(expected);
  }
}
