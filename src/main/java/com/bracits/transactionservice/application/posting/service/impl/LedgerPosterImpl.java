package com.bracits.transactionservice.application.posting.service.impl;

import com.bracits.transactionservice.application.constant.ApplicationConstants;
import com.bracits.transactionservice.application.posting.service.LedgerPoster;
import com.bracits.transactionservice.domain.ledger.enums.UnknownReason;
import com.bracits.transactionservice.domain.ledger.model.PostingOutcome;
import com.bracits.transactionservice.domain.ledger.model.PostingRequest;
import com.bracits.transactionservice.port.out.client.LedgerPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.resilience.InvocationRejectedException;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link LedgerPoster}.
 */
@Component
public final class LedgerPosterImpl implements LedgerPoster {

  private static final Logger LOG = LoggerFactory.getLogger(LedgerPosterImpl.class);

  private final LedgerPort ledger;

  public LedgerPosterImpl(LedgerPort ledger) {
    this.ledger = ledger;
  }

  @Override
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
