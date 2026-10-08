package com.bracits.transactionservice.application.mapper;

import com.bracits.transactionservice.domain.event.model.SendMoneyEvent;
import com.bracits.transactionservice.domain.txn.model.SendMoneyTxn;

/**
 * The only conversion {@link SendMoneyTxn} → {@link SendMoneyEvent} (spec 9). Used by the use case
 * after finalisation, by the republisher and by reconciliation's corrective
 * {@code SendMoneyCompleted} (spec 8.5). The event ID is deterministic, so every (re)publish of one
 * final state carries the same {@code message_id}.
 */
public interface SendMoneyEventMapper {

  /**
   * @param txn a row in a final status (COMPLETED or FAILED)
   * @throws IllegalArgumentException if the row is still INITIATED
   */
  SendMoneyEvent toEvent(SendMoneyTxn txn);
}
