package com.bracits.transactionservice.domain.limit.model;

import com.bracits.transactionservice.domain.enums.Product;

/**
 * A row of {@code limit_rule}: per-transaction range and daily/monthly amount and count limits per
 * tier.
 */
public record LimitRule(
    Product product,
    int kycTier,
    long perTxnMin,
    long perTxnMax,
    long dailyAmount,
    int dailyCount,
    long monthlyAmount,
    int monthlyCount) {

  /**
   * BR-02: amount within the tier's per-transaction minimum and maximum (inclusive).
   */
  public boolean allowsAmount(long amount) {
    return amount >= perTxnMin && amount <= perTxnMax;
  }
}
