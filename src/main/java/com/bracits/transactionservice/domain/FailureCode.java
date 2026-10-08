package com.bracits.transactionservice.domain;

/** Business failure codes returned to clients and stored in {@code send_money_txn.failure_code} (spec 7.1). */
public enum FailureCode {
  INSUFFICIENT_FUNDS,
  LIMIT_EXCEEDED,
  AMOUNT_OUT_OF_RANGE,
  SELF_TRANSFER,
  WALLET_NOT_FOUND,
  WALLET_INACTIVE,
  RECEIVER_NOT_ALLOWED,
  QUOTE_CHANGED,
  IDEMPOTENCY_CONFLICT,
  /** A definitive ledger rejection whose ledger code has no more specific mapping. */
  LEDGER_REJECTED
}
