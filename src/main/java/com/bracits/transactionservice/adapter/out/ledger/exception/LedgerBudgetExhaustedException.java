package com.bracits.transactionservice.adapter.out.ledger.exception;

import com.bracits.transactionservice.adapter.out.ledger.constant.LedgerApiConstants;

/**
 * Thrown before a retry when the remaining total ledger budget is shorter than one read timeout
 * (decision B7). Not retryable: it ends the retry loop with the outcome still unknown.
 */
public final class LedgerBudgetExhaustedException extends RuntimeException {

  public LedgerBudgetExhaustedException(long remainingMillis, long readTimeoutMillis) {
    super(LedgerApiConstants.MSG_BUDGET_EXHAUSTED.formatted(remainingMillis, readTimeoutMillis),
        null, false, false);
  }
}
