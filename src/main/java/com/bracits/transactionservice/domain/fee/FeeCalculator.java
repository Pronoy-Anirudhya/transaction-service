package com.bracits.transactionservice.domain.fee;

import com.bracits.transactionservice.domain.Pricing;
import com.bracits.transactionservice.domain.Product;

import java.util.Optional;

/** Strategy: prices one transaction (BR-04..BR-07). Empty when no fee rule matches the amount. */
public interface FeeCalculator {

  Optional<Pricing> price(Product product, int kycTier, long amount);
}
