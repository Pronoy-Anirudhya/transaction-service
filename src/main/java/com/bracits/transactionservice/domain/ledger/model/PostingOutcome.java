package com.bracits.transactionservice.domain.ledger.model;

import com.bracits.transactionservice.domain.enums.FailureCode;
import com.bracits.transactionservice.domain.ledger.enums.UnknownReason;

/**
 * Result of one ledger posting. Handled with an exhaustive {@code switch}.
 */
public sealed interface PostingOutcome {

  /**
   * 200 POSTED: every leg is committed in the ledger. {@code replay} = the posting already
   * existed.
   */
  record Posted(long ledgerTimestamp, boolean replay) implements PostingOutcome {

  }

  /**
   * 422 REJECTED: definitive; nothing was posted. {@code legIndex} is the root-cause leg
   * (1-based).
   */
  record Rejected(FailureCode code, int legIndex) implements PostingOutcome {

  }

  /**
   * The outcome is not known; the posting may or may not be committed. Never treat as a rejection.
   */
  record Unknown(UnknownReason reason) implements PostingOutcome {

  }
}
