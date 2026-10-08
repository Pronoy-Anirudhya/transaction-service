package com.bracits.transactionservice.domain;

/**
 * The fee of one Send Money and its split (BR-04..BR-07). All values are poisha.
 * Invariant: {@code vat + commission + feeIncome == fee}; no poisha is lost or created.
 */
public record Pricing(long fee, long vat, long commission, long feeIncome) {

  public static final Pricing FREE = new Pricing(0L, 0L, 0L, 0L);

  public Pricing {
    if (fee < 0 || vat < 0 || commission < 0 || feeIncome < 0) {
      throw new IllegalArgumentException(DomainMessages.PRICING_PARTS_NEGATIVE.formatted(fee, vat, commission, feeIncome));
    }
    if (Math.addExact(Math.addExact(vat, commission), feeIncome) != fee) {
      throw new IllegalArgumentException(DomainMessages.PRICING_PARTS_DO_NOT_SUM.formatted(fee, vat, commission, feeIncome));
    }
  }

  /** The sender is debited amount + fee (BR-08). */
  public long totalDebit(long amount) {
    return Math.addExact(amount, fee);
  }

}
