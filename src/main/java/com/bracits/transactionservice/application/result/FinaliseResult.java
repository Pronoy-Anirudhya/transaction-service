package com.bracits.transactionservice.application.result;

import com.bracits.transactionservice.domain.txn.model.SendMoneyTxn;

/**
 * Result of finalisation.
 */
public sealed interface FinaliseResult {

  /**
   * The row is COMPLETED or FAILED.
   */
  record Final(SendMoneyTxn txn) implements FinaliseResult {

  }

  /**
   * The row is still INITIATED; repair will resolve it.
   */
  record InDoubt() implements FinaliseResult {

  }
}
