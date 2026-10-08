package com.bracits.transactionservice.domain.ledger;

import com.bracits.transactionservice.domain.DomainMessages;
import com.bracits.transactionservice.domain.Product;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * An atomic multi-leg posting (one linked batch). {@code postingId} is the txnId (low 8 bits zero);
 * the ledger derives leg transfer IDs as {@code postingId | legIndex}. Re-sending the identical request is safe.
 */
public record PostingRequest(UUID postingId, Product product, long userData64, List<Leg> legs) {

  public PostingRequest {
    Objects.requireNonNull(postingId);
    Objects.requireNonNull(product);
    legs = List.copyOf(legs);
    if (legs.isEmpty()) {
      throw new IllegalArgumentException(DomainMessages.POSTING_WITHOUT_LEGS);
    }
  }
}
