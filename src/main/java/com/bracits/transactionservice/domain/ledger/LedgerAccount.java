package com.bracits.transactionservice.domain.ledger;

import java.util.Set;
import java.util.UUID;

/** An account to create in the ledger: {@code POST /internal/v1/accounts {accountId, code, flags, userData64}}. */
public record LedgerAccount(UUID accountId, LedgerAccountCode code, Set<LedgerAccountFlag> flags, long userData64) {

  public LedgerAccount {
    flags = Set.copyOf(flags);
  }
}
