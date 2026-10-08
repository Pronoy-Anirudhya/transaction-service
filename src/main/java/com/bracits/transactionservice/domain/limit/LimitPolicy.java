package com.bracits.transactionservice.domain.limit;

import com.bracits.transactionservice.domain.Product;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Strategy for finding the limit rule of a product and KYC tier. Rules come from a (cached) supplier. */
public final class LimitPolicy {

  private final Supplier<List<LimitRule>> rules;

  public LimitPolicy(Supplier<List<LimitRule>> rules) {
    this.rules = rules;
  }

  public Optional<LimitRule> ruleFor(Product product, int kycTier) {
    return rules.get().stream()
        .filter(rule -> rule.product() == product && rule.kycTier() == kycTier)
        .findFirst();
  }
}
