package com.bracits.transactionservice.adapter.out.ledger;

/**
 * The ledger answered 503 ({@code LEDGER_TIMEOUT}: outcome unknown, or {@code OVERLOADED}: not attempted); safe to
 * resend the identical request. Retryable; never leaves the ledger client.
 */
public final class LedgerTransientStatusException extends RuntimeException {

  public LedgerTransientStatusException(int status) {
    super(LedgerApiConstants.MSG_TRANSIENT_STATUS.formatted(status), null, false, false);
  }
}
