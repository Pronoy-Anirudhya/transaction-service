package com.bracits.transactionservice.port.out.exception;

/**
 * The ledger could not give an answer (503, I/O error, timeout or unexpected status). Safe to retry
 * later.
 */
public class LedgerUnavailableException extends RuntimeException {

  public LedgerUnavailableException(String message) {
    super(message);
  }

  public LedgerUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
