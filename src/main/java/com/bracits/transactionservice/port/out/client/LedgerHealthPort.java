package com.bracits.transactionservice.port.out.client;

/**
 * Whether ledger-service is currently reachable. ledger-service runs as a separate service; while
 * it is down, callers answer at once with 503 {@code LEDGER_UNAVAILABLE} instead of writing
 * anything or waiting through retries.
 */
public interface LedgerHealthPort {

  /**
   * {@code true} while the ledger's readiness probe answers 2xx (optimistic until the first
   * probe).
   */
  boolean isAvailable();
}
