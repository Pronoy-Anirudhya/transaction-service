package com.bracits.transactionservice.application;

import com.bracits.transactionservice.domain.ledger.PostingOutcome;
import com.bracits.transactionservice.domain.ledger.PostingRequest;
import com.bracits.transactionservice.domain.ledger.UnknownReason;
import com.bracits.transactionservice.port.out.LedgerPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.resilience.InvocationRejectedException;
import org.springframework.stereotype.Component;

/**
 * Calls {@link LedgerPort} and never throws: a saturated ledger bulkhead or any unexpected error becomes
 * {@code Unknown}, which keeps the row INITIATED for the repair worker (never FAILED without a 422).
 */
@Component
public final class LedgerPoster {

  private static final Logger LOG = LoggerFactory.getLogger(LedgerPoster.class);

  private final LedgerPort ledger;

  public LedgerPoster(LedgerPort ledger) {
    this.ledger = ledger;
  }

  public PostingOutcome post(PostingRequest request) {
    try {
      return ledger.post(request);
    } catch (InvocationRejectedException e) {
      return new PostingOutcome.Unknown(UnknownReason.OVERLOADED);
    } catch (RuntimeException e) {
      LOG.error(ApplicationConstants.LOG_LEDGER_CALL_FAILED, e);
      return new PostingOutcome.Unknown(UnknownReason.LEDGER_ERROR);
    }
  }
}
