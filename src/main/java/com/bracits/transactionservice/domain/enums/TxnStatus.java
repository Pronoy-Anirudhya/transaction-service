package com.bracits.transactionservice.domain.enums;

import com.bracits.transactionservice.domain.constant.DomainMessages;
import com.bracits.transactionservice.domain.model.Money;

/**
 * State machine of a Send Money transaction record. The same transitions are enforced in SQL by
 * compare-and-set updates ({@code WHERE status = 'INITIATED'}).
 *
 * <pre>
 * INITIATED --ledger POSTED-----------&gt; COMPLETED
 * INITIATED --ledger REJECTED (422)---&gt; FAILED
 * FAILED    --reconciliation finds legs posted ("ledger wins")--&gt; COMPLETED
 * </pre>
 */
public enum TxnStatus {
  INITIATED,
  COMPLETED,
  FAILED;

  public boolean canTransitionTo(TxnStatus target) {
    return switch (this) {
      case INITIATED -> target == COMPLETED || target == FAILED;
      case FAILED -> target == COMPLETED;
      case COMPLETED -> false;
    };
  }

  /**
   * Returns {@code target} if the transition is legal, otherwise throws.
   */
  public TxnStatus transitionTo(TxnStatus target) {
    if (!canTransitionTo(target)) {
      throw new IllegalStateException(DomainMessages.ILLEGAL_TRANSITION.formatted(this, target));
    }
    return target;
  }

  public boolean isFinal() {
    return this != INITIATED;
  }
}
