package com.bracits.transactionservice.application.posting.service;

import com.bracits.transactionservice.domain.ledger.model.PostingOutcome;
import com.bracits.transactionservice.domain.ledger.model.PostingRequest;
import com.bracits.transactionservice.port.out.client.LedgerPort;

/**
 * Calls {@link LedgerPort} and never throws: a saturated ledger bulkhead or any unexpected error
 * becomes {@code Unknown}, which keeps the row INITIATED for the repair worker (never FAILED
 * without a 422).
 */
public interface LedgerPoster {

  PostingOutcome post(PostingRequest request);
}
