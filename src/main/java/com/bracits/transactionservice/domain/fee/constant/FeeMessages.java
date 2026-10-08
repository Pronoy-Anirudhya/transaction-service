package com.bracits.transactionservice.domain.fee.constant;

/**
 * Exception messages of the fee arithmetic.
 */
public final class FeeMessages {

  public static final String NEGATIVE_FEE_INPUT = "Fee inputs must not be negative: %d";
  public static final String NEGATIVE_BPS = "Basis points must not be negative: %d";
  public static final String COMMISSION_BPS_TOO_HIGH = "Commission basis points must not exceed %d: %d";

  private FeeMessages() {
  }
}
