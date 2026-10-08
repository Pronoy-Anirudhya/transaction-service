package com.bracits.transactionservice.application.sendmoney.service;

import com.bracits.transactionservice.application.command.SendMoneyCommand;
import com.bracits.transactionservice.application.result.SendMoneyResult;

/**
 * FR-02 Send Money, exactly spec 5: in-memory validation and pricing → DB transaction #1 (insert +
 * conditional limit update) → ONE ledger call outside any DB transaction → compare-and-set
 * finalisation → 200 / 422 / 202. The event is published asynchronously after the final commit.
 * Implementations only orchestrate; each step is its own collaborator.
 */
public interface SendMoneyService {

  /**
   * Bulkhead on the posting endpoint (P10): when saturated, rejected before any write → 503 +
   * Retry-After.
   */
  SendMoneyResult send(SendMoneyCommand command);
}
