package com.bracits.transactionservice.domain.ledger;

/** Answer of {@code GET /internal/v1/postings/{postingId}?legs=n}. */
public enum PostingLookupStatus {
  POSTED,
  NOT_FOUND
}
