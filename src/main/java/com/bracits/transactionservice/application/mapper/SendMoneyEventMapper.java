package com.bracits.transactionservice.application.mapper;

import com.bracits.transactionservice.domain.DomainConstants;
import com.bracits.transactionservice.domain.Pricing;
import com.bracits.transactionservice.domain.event.EventIds;
import com.bracits.transactionservice.domain.event.EventType;
import com.bracits.transactionservice.domain.event.SendMoneyEvent;
import com.bracits.transactionservice.domain.txn.SendMoneyTxn;
import org.springframework.stereotype.Component;

/**
 * The only conversion {@link SendMoneyTxn} → {@link SendMoneyEvent} (spec 9). Used by the use case after finalisation,
 * by the republisher and by reconciliation's corrective {@code SendMoneyCompleted} (spec 8.5). The event ID is
 * deterministic, so every (re)publish of one final state carries the same {@code message_id}.
 */
@Component
public final class SendMoneyEventMapper {

  /**
   * @param txn a row in a final status (COMPLETED or FAILED)
   * @throws IllegalArgumentException if the row is still INITIATED
   */
  public SendMoneyEvent toEvent(SendMoneyTxn txn) {
    EventType eventType = EventType.forFinalStatus(txn.status());
    Pricing pricing = txn.pricing();
    return new SendMoneyEvent(
        EventIds.of(txn.txnId(), eventType),
        eventType,
        DomainConstants.EVENT_SCHEMA_VERSION,
        txn.completedAt().orElse(txn.createdAt()),
        txn.txnId(),
        txn.senderWalletId(),
        txn.receiverWalletId(),
        txn.amount(),
        pricing.fee(),
        pricing.vat(),
        pricing.commission(),
        pricing.feeIncome(),
        txn.currency(),
        txn.ledgerTimestamp(),
        txn.failureCode());
  }
}
