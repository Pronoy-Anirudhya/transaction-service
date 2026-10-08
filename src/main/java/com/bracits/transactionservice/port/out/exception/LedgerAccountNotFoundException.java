package com.bracits.transactionservice.port.out.exception;

/**
 * The ledger does not know the account (404, or 422 ACCOUNT_NOT_FOUND on funding).
 */
public class LedgerAccountNotFoundException extends RuntimeException {

  public LedgerAccountNotFoundException(String message) {
    super(message);
  }
}
