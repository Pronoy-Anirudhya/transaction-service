package com.bracits.transactionservice.port.out;

/** The ledger answered 409: an object with the same ID but different content exists. */
public class LedgerConflictException extends RuntimeException {

  public LedgerConflictException(String message) {
    super(message);
  }
}
