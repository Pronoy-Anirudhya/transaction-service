package com.bracits.transactionservice.port.out.client;

import com.bracits.transactionservice.domain.ledger.model.AccountBalance;
import com.bracits.transactionservice.domain.ledger.model.PostingLookup;
import com.bracits.transactionservice.port.out.exception.LedgerAccountNotFoundException;
import com.bracits.transactionservice.port.out.exception.LedgerUnavailableException;
import java.util.UUID;

/**
 * Read-only ledger queries. Throw {@link LedgerUnavailableException} when the ledger cannot
 * answer.
 */
public interface LedgerQueryPort {

  /**
   * {@code GET /internal/v1/postings/{postingId}?legs=n}: the posting's status and, when POSTED,
   * the ledger's transfer timestamp (used by reconciliation to finalise a row from the ledger's
   * truth). The timestamp is empty when the posting is NOT_FOUND, or when the ledger did not supply
   * it.
   */
  PostingLookup lookupPosting(UUID postingId, int legCount);

  /**
   * {@code GET /internal/v1/accounts/{id}/balance}. Throws {@link LedgerAccountNotFoundException}
   * on 404.
   */
  AccountBalance balance(UUID accountId);
}
