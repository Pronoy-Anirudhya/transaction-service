package com.bracits.transactionservice.domain.ledger.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Fixed ledger account IDs of the system accounts (spec 6.2), read from configuration.
 */
public record SystemAccounts(UUID feeIncome, UUID vatPayable, UUID commissionPayable,
                             UUID issuance) {

  public SystemAccounts {
    Objects.requireNonNull(feeIncome);
    Objects.requireNonNull(vatPayable);
    Objects.requireNonNull(commissionPayable);
    Objects.requireNonNull(issuance);
  }
}
