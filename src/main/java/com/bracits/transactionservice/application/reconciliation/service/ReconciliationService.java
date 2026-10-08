package com.bracits.transactionservice.application.reconciliation.service;

import com.bracits.transactionservice.application.result.ReconciliationResult;
import java.time.Instant;

/**
 * FR-08 / spec 8.5: compares final transaction records with ledger postings for a window. "The
 * ledger wins": a FAILED row whose legs are posted is flipped to COMPLETED (limits re-counted,
 * corrective {@code SendMoneyCompleted}, alert). Money is never adjusted to match the record.
 * INITIATED rows are left to the repair worker.
 */
public interface ReconciliationService {

  /**
   * Window {@code [from, to)} by txnId (time-ordered primary key; no extra index, decision B10).
   */
  ReconciliationResult reconcile(Instant from, Instant to);
}
