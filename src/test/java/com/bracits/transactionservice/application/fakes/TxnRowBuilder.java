package com.bracits.transactionservice.application.fakes;

import com.bracits.transactionservice.domain.FailureCode;
import com.bracits.transactionservice.domain.Pricing;
import com.bracits.transactionservice.domain.TxnStatus;
import com.bracits.transactionservice.domain.txn.NewSendMoneyTxn;
import com.bracits.transactionservice.domain.txn.RequestHash;
import com.bracits.transactionservice.domain.txn.SendMoneyTxn;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/** Mutable builder of stored {@link SendMoneyTxn} rows for seeding and for the in-memory repository. */
public final class TxnRowBuilder {

  private UUID txnId;
  private String clientRef = Fixtures.KEY;
  private RequestHash requestHash = new RequestHash(new byte[32]);
  private long senderWalletId = Fixtures.SENDER_ID;
  private long receiverWalletId = Fixtures.RECEIVER_ID;
  private long amount = Fixtures.AMOUNT;
  private Pricing pricing = Fixtures.STANDARD_PRICING;
  private String currency = Fixtures.CURRENCY;
  private Optional<String> reference = Optional.empty();
  private LocalDate businessDate = Fixtures.BUSINESS_DATE;
  private TxnStatus status = TxnStatus.INITIATED;
  private Optional<FailureCode> failureCode = Optional.empty();
  private int ledgerAttempts;
  private Optional<Instant> nextCheckAt = Optional.empty();
  private OptionalLong ledgerTimestamp = OptionalLong.empty();
  private Instant createdAt = Fixtures.NOW;
  private Optional<Instant> completedAt = Optional.empty();
  private Optional<Instant> eventPublishedAt = Optional.empty();

  private TxnRowBuilder() {
  }

  public static TxnRowBuilder row(UUID txnId) {
    TxnRowBuilder b = new TxnRowBuilder();
    b.txnId = txnId;
    return b;
  }

  /** The row {@code INSERT … status 'INITIATED', next_check_at = now()} creates (decision B1). */
  public static TxnRowBuilder inserted(NewSendMoneyTxn txn, Instant now) {
    TxnRowBuilder b = row(txn.txnId());
    b.clientRef = txn.clientRef();
    b.requestHash = txn.requestHash();
    b.senderWalletId = txn.senderWalletId();
    b.receiverWalletId = txn.receiverWalletId();
    b.amount = txn.amount();
    b.pricing = txn.pricing();
    b.currency = txn.currency();
    b.reference = Optional.ofNullable(txn.reference());
    b.businessDate = txn.businessDate();
    b.nextCheckAt = Optional.of(now);
    b.createdAt = now;
    return b;
  }

  public static TxnRowBuilder from(SendMoneyTxn t) {
    TxnRowBuilder b = row(t.txnId());
    b.clientRef = t.clientRef();
    b.requestHash = t.requestHash();
    b.senderWalletId = t.senderWalletId();
    b.receiverWalletId = t.receiverWalletId();
    b.amount = t.amount();
    b.pricing = t.pricing();
    b.currency = t.currency();
    b.reference = t.reference();
    b.businessDate = t.businessDate();
    b.status = t.status();
    b.failureCode = t.failureCode();
    b.ledgerAttempts = t.ledgerAttempts();
    b.nextCheckAt = t.nextCheckAt();
    b.ledgerTimestamp = t.ledgerTimestamp();
    b.createdAt = t.createdAt();
    b.completedAt = t.completedAt();
    b.eventPublishedAt = t.eventPublishedAt();
    return b;
  }

  public TxnRowBuilder clientRef(String value) {
    clientRef = value;
    return this;
  }

  public TxnRowBuilder receiverWalletId(long value) {
    receiverWalletId = value;
    return this;
  }

  public TxnRowBuilder senderWalletId(long value) {
    senderWalletId = value;
    return this;
  }

  public TxnRowBuilder amount(long value) {
    amount = value;
    return this;
  }

  public TxnRowBuilder pricing(Pricing value) {
    pricing = value;
    return this;
  }

  public TxnRowBuilder reference(Optional<String> value) {
    reference = value;
    return this;
  }

  public TxnRowBuilder status(TxnStatus value) {
    status = value;
    return this;
  }

  public TxnRowBuilder failureCode(Optional<FailureCode> value) {
    failureCode = value;
    return this;
  }

  public TxnRowBuilder ledgerAttempts(int value) {
    ledgerAttempts = value;
    return this;
  }

  public TxnRowBuilder nextCheckAt(Optional<Instant> value) {
    nextCheckAt = value;
    return this;
  }

  public TxnRowBuilder ledgerTimestamp(OptionalLong value) {
    ledgerTimestamp = value;
    return this;
  }

  public TxnRowBuilder createdAt(Instant value) {
    createdAt = value;
    return this;
  }

  public TxnRowBuilder completedAt(Optional<Instant> value) {
    completedAt = value;
    return this;
  }

  public TxnRowBuilder eventPublishedAt(Optional<Instant> value) {
    eventPublishedAt = value;
    return this;
  }

  /** A COMPLETED row as the request path leaves it. */
  public TxnRowBuilder completed(long ledgerTs, Instant at) {
    return status(TxnStatus.COMPLETED).ledgerTimestamp(OptionalLong.of(ledgerTs)).completedAt(Optional.of(at));
  }

  /** A FAILED row as a definitive ledger rejection leaves it. */
  public TxnRowBuilder failed(FailureCode code, Instant at) {
    return status(TxnStatus.FAILED).failureCode(Optional.of(code)).completedAt(Optional.of(at));
  }

  public SendMoneyTxn build() {
    return new SendMoneyTxn(txnId, clientRef, requestHash, senderWalletId, receiverWalletId, amount, pricing,
        currency, reference, businessDate, status, failureCode, ledgerAttempts, nextCheckAt, ledgerTimestamp,
        createdAt, completedAt, eventPublishedAt);
  }
}
