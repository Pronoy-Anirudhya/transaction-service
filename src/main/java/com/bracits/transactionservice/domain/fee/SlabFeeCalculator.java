package com.bracits.transactionservice.domain.fee;

import com.bracits.transactionservice.domain.Pricing;
import com.bracits.transactionservice.domain.Product;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Strategy: slab pricing from {@code fee_rule} (BR-04..BR-07). The first active rule whose product, tier and
 * inclusive amount range cover the transaction wins. Rules come from a (cached) supplier.
 */
public final class SlabFeeCalculator implements FeeCalculator {

  private final Supplier<List<FeeRule>> rules;

  public SlabFeeCalculator(Supplier<List<FeeRule>> rules) {
    this.rules = rules;
  }

  @Override
  public Optional<Pricing> price(Product product, int kycTier, long amount) {
    return rules.get().stream()
        .filter(rule -> rule.covers(product, kycTier, amount))
        .findFirst()
        .map(rule -> FeeMath.split(fee(rule, amount), rule.vatBps(), rule.commissionBps()));
  }

  /** Raw slab fee clamped to {@code [feeMin, feeMax]}; an empty {@code feeMax} means no upper clamp. */
  static long fee(FeeRule rule, long amount) {
    long raw = switch (rule.feeType()) {
      case FLAT -> rule.feeValue();
      case PERCENT -> FeeMath.percentHalfUp(amount, rule.feeValue());
    };
    long fee = Math.max(raw, rule.feeMin());
    if (rule.feeMax().isPresent()) {
      fee = Math.min(fee, rule.feeMax().getAsLong());
    }
    return fee;
  }
}
