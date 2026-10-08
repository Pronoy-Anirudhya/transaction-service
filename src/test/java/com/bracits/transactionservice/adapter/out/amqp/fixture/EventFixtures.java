package com.bracits.transactionservice.adapter.out.amqp.fixture;

import com.bracits.transactionservice.config.properties.EventsProperties;
import com.bracits.transactionservice.domain.constant.DomainConstants;
import com.bracits.transactionservice.domain.enums.FailureCode;
import com.bracits.transactionservice.domain.enums.TxnStatus;
import com.bracits.transactionservice.domain.event.enums.EventType;
import com.bracits.transactionservice.domain.event.factory.EventIds;
import com.bracits.transactionservice.domain.event.model.SendMoneyEvent;
import com.bracits.transactionservice.domain.model.Pricing;
import com.bracits.transactionservice.domain.txn.model.RequestHash;
import com.bracits.transactionservice.domain.txn.model.SendMoneyTxn;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Test data for the event adapter and the republisher.
 */
public final class EventFixtures {

  public static final Pricing PRICING = new Pricing(500, 65, 87, 348);
  public static final Instant CREATED_AT = Instant.parse("2026-10-07T09:14:03.100Z");
  public static final Instant COMPLETED_AT = Instant.parse("2026-10-07T09:14:03.211Z");
  public static final long LEDGER_TS = 1791350858928000000L;

  private EventFixtures() {
  }

  /**
   * A fresh, unique txnId.
   */
  public static UUID newTxnId() {
    return UUID.randomUUID();
  }

  public static SendMoneyEvent completedEvent(UUID txnId) {
    return new SendMoneyEvent(EventIds.of(txnId, EventType.SEND_MONEY_COMPLETED),
        EventType.SEND_MONEY_COMPLETED,
        DomainConstants.EVENT_SCHEMA_VERSION, COMPLETED_AT, txnId, 42, 77, 100_000, PRICING.fee(),
        PRICING.vat(),
        PRICING.commission(), PRICING.feeIncome(), DomainConstants.CURRENCY_BDT,
        OptionalLong.of(LEDGER_TS),
        Optional.empty());
  }

  public static SendMoneyEvent failedEvent(UUID txnId) {
    return new SendMoneyEvent(EventIds.of(txnId, EventType.SEND_MONEY_FAILED),
        EventType.SEND_MONEY_FAILED,
        DomainConstants.EVENT_SCHEMA_VERSION, COMPLETED_AT, txnId, 42, 77, 100_000, PRICING.fee(),
        PRICING.vat(),
        PRICING.commission(), PRICING.feeIncome(), DomainConstants.CURRENCY_BDT,
        OptionalLong.empty(),
        Optional.of(FailureCode.INSUFFICIENT_FUNDS));
  }

  /**
   * The exact wire format of spec 9 for a fixture event.
   */
  public static String expectedJson(SendMoneyEvent event) {
    String ledgerTimestamp = event.ledgerTimestamp().isPresent()
        ? Long.toString(event.ledgerTimestamp().getAsLong()) : "null";
    String failureCode = event.failureCode().map(code -> "\"" + code.name() + "\"").orElse("null");

    return "{\"eventId\":\"" + event.eventId() + "\",\"eventType\":\"" + event.eventType()
        .eventName()
        + "\",\"schemaVersion\":1,\"occurredAt\":\"2026-10-07T09:14:03.211Z\",\"txnId\":\""
        + event.txnId()
        + "\",\"senderWalletId\":42,\"receiverWalletId\":77,\"amount\":100000,\"fee\":500,\"vat\":65,"
        + "\"commission\":87,\"feeIncome\":348,\"currency\":\"BDT\",\"ledgerTimestamp\":"
        + ledgerTimestamp
        + ",\"failureCode\":" + failureCode + "}";
  }

  public static SendMoneyTxn completedTxn(UUID txnId) {
    return txn(txnId, TxnStatus.COMPLETED, Optional.empty(), OptionalLong.of(LEDGER_TS),
        Optional.of(COMPLETED_AT));
  }

  public static SendMoneyTxn failedTxn(UUID txnId) {
    return txn(txnId, TxnStatus.FAILED, Optional.of(FailureCode.INSUFFICIENT_FUNDS),
        OptionalLong.empty(),
        Optional.of(COMPLETED_AT));
  }

  public static SendMoneyTxn txn(UUID txnId, TxnStatus status, Optional<FailureCode> failureCode,
      OptionalLong ledgerTs, Optional<Instant> completedAt) {
    return new SendMoneyTxn(txnId, "client-ref-1", new RequestHash(new byte[32]), 42, 77, 100_000,
        PRICING,
        DomainConstants.CURRENCY_BDT, Optional.empty(), LocalDate.of(2026, 10, 7), status,
        failureCode, 1,
        Optional.empty(), ledgerTs, CREATED_AT, completedAt, Optional.empty());
  }

  public static EventsProperties properties(Duration confirmTimeout, int flushBatchSize) {
    return new EventsProperties("mfs.transactions", confirmTimeout, Duration.ofMillis(100),
        flushBatchSize,
        new EventsProperties.Republish(Duration.ofSeconds(5), 500, Duration.ofSeconds(10),
            Duration.ofSeconds(30)));
  }
}
