package com.plantshop.common.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

  @ParameterizedTest
  @CsvSource({"26.99,26.99", "26.9,26.90", "26,26.00", "0.005,0.01", "0.004,0.00", "1E+2,100.00"})
  @DisplayName("[BR-10] amounts are formatted with two decimals, rounded HALF_UP")
  void format_roundsHalfUpToTwoDecimals(String input, String expected) {
    assertThat(Money.format(new BigDecimal(input))).isEqualTo(expected);
  }

  @Test
  void normalize_null_isRejected() {
    assertThatNullPointerException().isThrownBy(() -> Money.normalize(null));
  }
}
