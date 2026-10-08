package com.bracits.transactionservice.application.result;

import com.bracits.transactionservice.domain.FailureCode;
import com.bracits.transactionservice.domain.Pricing;
import com.bracits.transactionservice.domain.TxnStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** What the Send Money response needs about one transaction. */
public record TxnSummary(
    UUID txnId,
    TxnStatus status,
    long amount,
    Pricing pricing,
    Optional<FailureCode> failureCode,
    Optional<Instant> completedAt) {

  public long totalDebit() {
    return pricing.totalDebit(amount);
  }
}
