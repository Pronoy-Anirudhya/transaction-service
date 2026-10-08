package com.bracits.transactionservice.domain.ledger.model;

import com.bracits.transactionservice.domain.ledger.enums.PostingLookupStatus;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Answer of a posting lookup. {@code ledgerTimestamp} is the ledger's transfer timestamp; it is
 * present when the posting is {@link PostingLookupStatus#POSTED} (if the ledger supplied it) and
 * empty when {@code NOT_FOUND}.
 */
public record PostingLookup(PostingLookupStatus status, OptionalLong ledgerTimestamp) {

  public PostingLookup {
    Objects.requireNonNull(status);
    Objects.requireNonNull(ledgerTimestamp);
  }
}
