package com.bracits.transactionservice.adapter.out.ledger;

/**
 * The ledger gave no answer: retries or budget exhausted ({@link #isTransient()} = true), or the call failed for a
 * non-retryable local reason. Internal to the ledger client; mapped to a port result or exception.
 */
final class LedgerCallFailedException extends RuntimeException {

  private final int attempts;
  private final boolean transientFailure;

  LedgerCallFailedException(int attempts, boolean transientFailure, Throwable cause) {
    super(LedgerApiConstants.MSG_CALL_FAILED.formatted(attempts), cause);
    this.attempts = attempts;
    this.transientFailure = transientFailure;
  }

  int attempts() {
    return attempts;
  }

  /** {@code true} if every attempt failed with 503 / I/O / timeout, or the budget ran out. */
  boolean isTransient() {
    return transientFailure;
  }
}
