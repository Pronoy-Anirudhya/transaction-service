package com.bracits.transactionservice.domain.ledger.enums;

/**
 * Why a ledger posting outcome is unknown. The row stays INITIATED and the repair worker resolves
 * it.
 */
public enum UnknownReason {
  /**
   * 503 / I/O error / read timeout, still failing after the retries and the total budget.
   */
  LEDGER_TIMEOUT,
  /**
   * 409: same posting ID with different content. A bug; alert.
   */
  POSTING_CONFLICT,
  /**
   * 500 or another unexpected answer. Alert.
   */
  LEDGER_ERROR,
  /**
   * The local bulkhead on the ledger port was saturated, so the call was not made.
   */
  OVERLOADED
}
