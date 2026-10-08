package com.bracits.transactionservice.application.result;

import com.bracits.transactionservice.domain.FailureCode;

import java.util.Optional;
import java.util.UUID;

/** Outcome of the Send Money use case; the controller maps it with an exhaustive {@code switch}. */
public sealed interface SendMoneyResult {

  /** 200: the ledger posted every leg and the row is COMPLETED. */
  record Completed(TxnSummary txn) implements SendMoneyResult {
  }

  /** 202: the row is INITIATED and the outcome is still unknown; poll the status. */
  record Processing(TxnSummary txn) implements SendMoneyResult {
  }

  /**
   * 422 (business failure) or 409 ({@code IDEMPOTENCY_CONFLICT}, {@code QUOTE_CHANGED}). {@code txnId} is present when a
   * transaction record exists (ledger rejection, or replay of a FAILED row).
   */
  record Rejected(FailureCode code, Optional<UUID> txnId) implements SendMoneyResult {
  }

  /** 400: the quote token is malformed or its signature is wrong. */
  record InvalidQuoteToken() implements SendMoneyResult {
  }
}
