package com.bracits.transactionservice.domain.limit.policy;

import com.bracits.transactionservice.domain.enums.Product;
import com.bracits.transactionservice.domain.limit.model.LimitRule;
import java.util.Optional;

/**
 * Strategy for finding the limit rule of a product and KYC tier. Rules come from a (cached)
 * supplier.
 */
public interface LimitPolicy {

  Optional<LimitRule> ruleFor(Product product, int kycTier);
}
