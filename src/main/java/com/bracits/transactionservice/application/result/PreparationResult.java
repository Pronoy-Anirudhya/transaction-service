package com.bracits.transactionservice.application.result;

import com.bracits.transactionservice.domain.enums.FailureCode;
import com.bracits.transactionservice.domain.limit.model.LimitRule;
import com.bracits.transactionservice.domain.model.Pricing;
import com.bracits.transactionservice.domain.wallet.model.Wallet;
import java.util.Optional;

/**
 * Result of the in-memory preparation.
 */
public sealed interface PreparationResult {

  /**
   * Every rule passed and the transaction is priced.
   */
  record Ready(Wallet sender, Wallet receiver, LimitRule limitRule, Pricing pricing) implements
      PreparationResult {

  }

  /**
   * A rule failed; {@code sender} is kept for the idempotent-replay look-up.
   */
  record Failed(FailureCode code, Optional<Wallet> sender) implements PreparationResult {

  }
}
