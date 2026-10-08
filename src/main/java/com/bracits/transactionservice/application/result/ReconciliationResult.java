package com.bracits.transactionservice.application.result;

import com.bracits.transactionservice.domain.TxnStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** FR-08 report: rows checked in {@code [from, to)} and every disagreement with the ledger. */
public record ReconciliationResult(
    Instant from, Instant to, int checked, boolean truncated, List<Mismatch> mismatches) {

  public ReconciliationResult {
    mismatches = List.copyOf(mismatches);
  }

  /** One disagreement; {@code recordStatus} is the status before any action. */
  public record Mismatch(UUID txnId, TxnStatus recordStatus, LedgerView ledgerStatus, Action action) {
  }

  /** What the ledger said. */
  public enum LedgerView {
    POSTED,
    NOT_FOUND,
    UNKNOWN
  }

  /** What reconciliation did. */
  public enum Action {
    /** FAILED row with posted legs: flipped to COMPLETED, limits re-counted, corrective event, alert ("ledger wins"). */
    FLIPPED_TO_COMPLETED,
    /** COMPLETED row without posted legs: cannot be fixed automatically; high-severity alert. */
    ALERT_RAISED,
    /** The ledger could not be asked; check again later. */
    NOT_CHECKED
  }
}
