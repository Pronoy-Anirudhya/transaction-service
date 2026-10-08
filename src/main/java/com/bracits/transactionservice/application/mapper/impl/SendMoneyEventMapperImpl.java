package com.bracits.transactionservice.application.mapper.impl;

import com.bracits.transactionservice.application.mapper.SendMoneyEventMapper;
import com.bracits.transactionservice.domain.constant.DomainConstants;
import com.bracits.transactionservice.domain.event.enums.EventType;
import com.bracits.transactionservice.domain.event.factory.EventIds;
import com.bracits.transactionservice.domain.event.model.SendMoneyEvent;
import com.bracits.transactionservice.domain.model.Pricing;
import com.bracits.transactionservice.domain.txn.model.SendMoneyTxn;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link SendMoneyEventMapper}.
 */
@Component
public final class SendMoneyEventMapperImpl implements SendMoneyEventMapper {

  /**
   * @param txn a row in a final status (COMPLETED or FAILED)
   * @throws IllegalArgumentException if the row is still INITIATED
   */
  @Override
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
