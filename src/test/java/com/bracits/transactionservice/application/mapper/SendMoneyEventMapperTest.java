package com.bracits.transactionservice.application.mapper;

import com.bracits.transactionservice.adapter.out.amqp.EventFixtures;
import com.bracits.transactionservice.domain.DomainConstants;
import com.bracits.transactionservice.domain.FailureCode;
import com.bracits.transactionservice.domain.TxnStatus;
import com.bracits.transactionservice.domain.event.EventIds;
import com.bracits.transactionservice.domain.event.EventType;
import com.bracits.transactionservice.domain.event.SendMoneyEvent;
import com.bracits.transactionservice.domain.txn.SendMoneyTxn;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SendMoneyEventMapperTest {

  private final SendMoneyEventMapper mapper = new SendMoneyEventMapper();

  @Test
  void completedTxnMapsToSendMoneyCompleted() {
    UUID txnId = EventFixtures.newTxnId();

    SendMoneyEvent event = mapper.toEvent(EventFixtures.completedTxn(txnId));

    assertThat(event).isEqualTo(EventFixtures.completedEvent(txnId));
    assertThat(event.eventType()).isEqualTo(EventType.SEND_MONEY_COMPLETED);
    assertThat(event.eventId()).isEqualTo(EventIds.of(txnId, EventType.SEND_MONEY_COMPLETED));
    assertThat(event.schemaVersion()).isEqualTo(DomainConstants.EVENT_SCHEMA_VERSION);
    assertThat(event.occurredAt()).isEqualTo(EventFixtures.COMPLETED_AT);
    assertThat(event.ledgerTimestamp()).hasValue(EventFixtures.LEDGER_TS);
    assertThat(event.failureCode()).isEmpty();
    assertThat(event.fee()).isEqualTo(500);
    assertThat(event.vat()).isEqualTo(65);
    assertThat(event.commission()).isEqualTo(87);
    assertThat(event.feeIncome()).isEqualTo(348);
    assertThat(event.currency()).isEqualTo(DomainConstants.CURRENCY_BDT);
  }

  @Test
  void failedTxnMapsToSendMoneyFailed() {
    UUID txnId = EventFixtures.newTxnId();

    SendMoneyEvent event = mapper.toEvent(EventFixtures.failedTxn(txnId));

    assertThat(event).isEqualTo(EventFixtures.failedEvent(txnId));
    assertThat(event.eventType()).isEqualTo(EventType.SEND_MONEY_FAILED);
    assertThat(event.eventId()).isEqualTo(EventIds.of(txnId, EventType.SEND_MONEY_FAILED));
    assertThat(event.ledgerTimestamp()).isEmpty();
    assertThat(event.failureCode()).contains(FailureCode.INSUFFICIENT_FUNDS);
  }

  @Test
  void occurredAtFallsBackToCreatedAt() {
    SendMoneyTxn txn = EventFixtures.txn(EventFixtures.newTxnId(), TxnStatus.FAILED,
        Optional.of(FailureCode.LEDGER_REJECTED), OptionalLong.empty(), Optional.empty());

    assertThat(mapper.toEvent(txn).occurredAt()).isEqualTo(EventFixtures.CREATED_AT);
  }

  @Test
  void sameRowAlwaysYieldsSameEventId() {
    UUID txnId = EventFixtures.newTxnId();

    assertThat(mapper.toEvent(EventFixtures.completedTxn(txnId)).eventId())
        .isEqualTo(mapper.toEvent(EventFixtures.completedTxn(txnId)).eventId());
  }

  @Test
  void initiatedTxnHasNoEvent() {
    SendMoneyTxn txn = EventFixtures.txn(EventFixtures.newTxnId(), TxnStatus.INITIATED, Optional.empty(),
        OptionalLong.empty(), Optional.empty());

    assertThatIllegalArgumentException().isThrownBy(() -> mapper.toEvent(txn));
  }
}
