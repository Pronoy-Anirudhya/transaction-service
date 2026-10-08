package com.bracits.transactionservice.adapter.out.ledger;

import java.time.Duration;

/**
 * Per-call guard for decision B7: the first attempt always runs; a retry starts only while the remaining total
 * budget is at least one read timeout, so the call as a whole never overruns the budget. Used by one thread.
 */
final class LedgerAttemptBudget {

  private final long deadlineNanos;
  private final long readTimeoutNanos;
  private int attempts;

  LedgerAttemptBudget(Duration totalBudget, Duration readTimeout) {
    this.deadlineNanos = System.nanoTime() + totalBudget.toNanos();
    this.readTimeoutNanos = readTimeout.toNanos();
  }

  /** Call before every attempt. */
  void beforeAttempt() {
    if (attempts > 0) {
      long remaining = deadlineNanos - System.nanoTime();
      if (remaining < readTimeoutNanos) {
        throw new LedgerBudgetExhaustedException(
            Duration.ofNanos(Math.max(remaining, 0)).toMillis(), Duration.ofNanos(readTimeoutNanos).toMillis());
      }
    }
    attempts++;
  }

  int attempts() {
    return attempts;
  }
}
