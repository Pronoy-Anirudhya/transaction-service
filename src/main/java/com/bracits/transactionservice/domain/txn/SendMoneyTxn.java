package com.bracits.transactionservice.domain.txn;

import com.bracits.transactionservice.domain.FailureCode;
import com.bracits.transactionservice.domain.Pricing;
import com.bracits.transactionservice.domain.TxnStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * The Send Money aggregate as stored in {@code send_money_txn}. Nullable columns are Optional.
 * {@code reference} is empty when the client sent none.
 */
public record SendMoneyTxn(
    UUID txnId,
    String clientRef,
    RequestHash requestHash,
    long senderWalletId,
    long receiverWalletId,
    long amount,
    Pricing pricing,
    String currency,
    Optional<String> reference,
    LocalDate businessDate,
    TxnStatus status,
    Optional<FailureCode> failureCode,
    int ledgerAttempts,
    Optional<Instant> nextCheckAt,
    OptionalLong ledgerTimestamp,
    Instant createdAt,
    Optional<Instant> completedAt,
    Optional<Instant> eventPublishedAt) {

  public long totalDebit() {
    return pricing.totalDebit(amount);
  }
}
