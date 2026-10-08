package com.bracits.transactionservice.domain.fee.model;

import com.bracits.transactionservice.domain.enums.Product;
import com.bracits.transactionservice.domain.fee.enums.FeeType;
import java.util.OptionalLong;

/**
 * A row of {@code fee_rule}: one slab of a product and tier. The slab range is inclusive.
 * {@code feeMax} is empty when the column is NULL (no upper clamp).
 */
public record FeeRule(
    long ruleId,
    Product product,
    int kycTier,
    long minAmount,
    long maxAmount,
    FeeType feeType,
    long feeValue,
    long feeMin,
    OptionalLong feeMax,
    int vatBps,
    int commissionBps,
    boolean active) {

  public boolean covers(Product candidateProduct, int tier, long amount) {
    return active && product == candidateProduct && kycTier == tier && amount >= minAmount
        && amount <= maxAmount;
  }
}
