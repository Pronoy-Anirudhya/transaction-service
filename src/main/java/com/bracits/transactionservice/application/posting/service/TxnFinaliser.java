package com.bracits.transactionservice.application.posting.service;

import com.bracits.transactionservice.application.result.FinaliseResult;
import com.bracits.transactionservice.domain.ledger.model.PostingOutcome;
import com.bracits.transactionservice.domain.txn.model.SendMoneyTxn;
import java.time.Duration;
import java.util.UUID;

/**
 * Spec 5 step 8 / 8.3 outcome table, shared by the request path and the repair worker. Each
 * finalising write is ONE auto-commit compare-and-set statement; the event is handed to the
 * publisher only after it committed (spec 9 rule 1). A definitive 422 is the only way to FAILED.
 */
public interface TxnFinaliser {

  /**
   * @param recheckDelay when the outcome is unknown, the row is rechecked by repair after this
   *                     delay
   */
  FinaliseResult finalise(UUID txnId, PostingOutcome outcome, Duration recheckDelay);

  /**
   * Hands the event of a committed final row to the asynchronous publisher.
   */
  SendMoneyTxn publish(SendMoneyTxn txn);
}
