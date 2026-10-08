package com.bracits.transactionservice.domain.ledger.planner;

import com.bracits.transactionservice.domain.ledger.model.PostingRequest;
import com.bracits.transactionservice.domain.model.Pricing;
import com.bracits.transactionservice.domain.wallet.model.Wallet;
import java.util.UUID;

/**
 * Builds the linked Send Money posting (spec 6.2): principal, fee income, VAT, commission, all
 * debited from the sender. Legs with a zero amount are omitted (BR-09), so leg indexes are
 * positions in the posted batch.
 */
public interface LegPlanner {

  PostingRequest plan(UUID txnId, Wallet sender, Wallet receiver, long amount,
      Pricing pricing);

  /**
   * Number of legs {@link #plan} produces for this pricing (for
   * {@code GET /postings/{id}?legs=n}).
   */
  static int legCount(Pricing pricing) {
    return 1 + positive(pricing.feeIncome()) + positive(pricing.vat()) + positive(
        pricing.commission());
  }

  private static int positive(long amount) {
    return amount > 0 ? 1 : 0;
  }
}
