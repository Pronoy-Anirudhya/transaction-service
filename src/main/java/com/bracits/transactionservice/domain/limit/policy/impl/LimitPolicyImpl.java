package com.bracits.transactionservice.domain.limit.policy.impl;

import com.bracits.transactionservice.domain.enums.Product;
import com.bracits.transactionservice.domain.limit.model.LimitRule;
import com.bracits.transactionservice.domain.limit.policy.LimitPolicy;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Default implementation of {@link LimitPolicy}.
 */
public final class LimitPolicyImpl implements LimitPolicy {

  private final Supplier<List<LimitRule>> rules;

  public LimitPolicyImpl(Supplier<List<LimitRule>> rules) {
    this.rules = rules;
  }

  @Override
  public Optional<LimitRule> ruleFor(Product product, int kycTier) {
    return rules.get().stream()
        .filter(rule -> rule.product() == product && rule.kycTier() == kycTier)
        .findFirst();
  }
}
