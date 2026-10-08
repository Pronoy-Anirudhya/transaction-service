package com.bracits.transactionservice.domain.ledger.model;

import com.bracits.transactionservice.domain.constant.DomainMessages;
import com.bracits.transactionservice.domain.ledger.enums.LegCode;
import java.util.Objects;
import java.util.UUID;

/**
 * One transfer of a linked posting: debit one ledger account, credit another.
 */
public record Leg(UUID debitAccountId, UUID creditAccountId, long amount, LegCode code) {

  public Leg {
    Objects.requireNonNull(debitAccountId);
    Objects.requireNonNull(creditAccountId);
    Objects.requireNonNull(code);
    if (amount <= 0) {
      throw new IllegalArgumentException(DomainMessages.LEG_AMOUNT_NOT_POSITIVE.formatted(amount));
    }
  }
}
