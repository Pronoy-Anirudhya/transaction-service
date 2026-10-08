package com.bracits.transactionservice.port.out.client;

import com.bracits.transactionservice.domain.ledger.model.PostingOutcome;
import com.bracits.transactionservice.domain.ledger.model.PostingRequest;

/**
 * Atomic multi-leg posting ({@code POST /internal/v1/postings}). Implementations apply the deadline
 * hierarchy and retry policy of spec 8.3, always resending the identical request, and never throw
 * for ledger answers: 200 → Posted, 422 → Rejected, everything else → Unknown. Guarded by a
 * rejecting {@code @ConcurrencyLimit}; when saturated the proxy throws
 * {@code org.springframework.resilience.InvocationRejectedException} without calling the ledger.
 */
public interface LedgerPort {

  PostingOutcome post(PostingRequest request);
}
