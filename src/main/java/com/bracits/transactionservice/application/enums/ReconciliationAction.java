package com.bracits.transactionservice.application.enums;

/**
 * What reconciliation did.
 */
public enum ReconciliationAction {
  /**
   * FAILED row with posted legs: flipped to COMPLETED, limits re-counted, corrective event, alert
   * ("ledger wins").
   */
  FLIPPED_TO_COMPLETED,
  /**
   * COMPLETED row without posted legs: cannot be fixed automatically; high-severity alert.
   */
  ALERT_RAISED,
  /**
   * The ledger could not be asked; check again later.
   */
  NOT_CHECKED
}
