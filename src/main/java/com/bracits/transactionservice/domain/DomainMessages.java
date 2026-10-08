package com.bracits.transactionservice.domain;

/** Exception and invariant messages raised by domain types. */
public final class DomainMessages {

  public static final String NEGATIVE_AMOUNT = "Money amounts must not be negative: %d";
  public static final String PRICING_PARTS_NEGATIVE = "Pricing parts must not be negative: fee=%d, vat=%d, commission=%d, feeIncome=%d";
  public static final String PRICING_PARTS_DO_NOT_SUM = "VAT + commission + fee income must equal the fee: fee=%d, vat=%d, commission=%d, feeIncome=%d";
  public static final String POSTING_WITHOUT_LEGS = "A posting needs at least one leg";
  public static final String LEG_AMOUNT_NOT_POSITIVE = "Leg amounts must be positive: %d";
  public static final String ILLEGAL_TRANSITION = "Illegal status transition %s -> %s";
  public static final String UNKNOWN_PRODUCT = "Unknown product: %s";
  public static final String UNKNOWN_EVENT_TYPE = "Unknown event type: %s";

  private DomainMessages() {
  }
}
