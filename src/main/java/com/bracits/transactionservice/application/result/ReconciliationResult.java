package com.bracits.transactionservice.application.result;

import com.bracits.transactionservice.application.enums.ReconciliationAction;
import com.bracits.transactionservice.application.enums.ReconciliationLedgerView;
import com.bracits.transactionservice.domain.enums.TxnStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * FR-08 report: rows checked in {@code [from, to)} and every disagreement with the ledger.
 */
public record ReconciliationResult(
    Instant from, Instant to, int checked, boolean truncated, List<Mismatch> mismatches) {

  public ReconciliationResult {
    mismatches = List.copyOf(mismatches);
  }

  /**
   * One disagreement; {@code recordStatus} is the status before any action.
   */
  public record Mismatch(UUID txnId, TxnStatus recordStatus, ReconciliationLedgerView ledgerStatus,
                         ReconciliationAction action) {

  }
}
