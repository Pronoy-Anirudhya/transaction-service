package com.bracits.transactionservice.application.result;

import com.bracits.transactionservice.domain.FailureCode;
import com.bracits.transactionservice.domain.Pricing;

import java.time.Instant;

/** Outcome of the quote use case (FR-01). */
public sealed interface QuoteResult {

  /** 200: priced; {@code receiverName} is already masked. */
  record Quoted(String receiverName, long amount, Pricing pricing, String quoteToken, Instant expiresAt)
      implements QuoteResult {

    public long totalDebit() {
      return pricing.totalDebit(amount);
    }
  }

  /** 422: a rule failed (BR-01, BR-02) or no fee rule matches. */
  record Rejected(FailureCode code) implements QuoteResult {
  }
}
