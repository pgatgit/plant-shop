package com.plantshop.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Money helpers: USD amounts with scale 2 and {@code HALF_UP} rounding (BR-10). */
public final class Money {

  /** The only supported currency (D19). */
  public static final String CURRENCY = "USD";

  /** Number of decimal places for all amounts. */
  public static final int SCALE = 2;

  private Money() {}

  /**
   * Rounds an amount to the money scale.
   *
   * @param amount the amount
   * @return the amount with scale 2, rounded {@code HALF_UP}
   */
  public static BigDecimal normalize(BigDecimal amount) {
    return Objects.requireNonNull(amount, "amount").setScale(SCALE, RoundingMode.HALF_UP);
  }

  /**
   * Formats an amount for the API: plain decimal string with exactly two decimals, e.g. {@code
   * "26.99"}. Strings avoid binary floating point rounding in JavaScript clients.
   *
   * @param amount the amount
   * @return the formatted amount
   */
  public static String format(BigDecimal amount) {
    return normalize(amount).toPlainString();
  }
}
