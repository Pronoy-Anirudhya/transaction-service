package com.bracits.transactionservice.port.out;

import com.bracits.transactionservice.domain.ledger.AccountCreation;
import com.bracits.transactionservice.domain.ledger.LedgerAccount;

import java.util.UUID;

/** Ledger account management and funding (test-profile support endpoints). Both calls are idempotent. */
public interface LedgerAccountsPort {

  /**
   * {@code POST /internal/v1/accounts}: 201 → CREATED, 200 → ALREADY_EXISTS. 409 (exists with different fields) →
   * {@link LedgerConflictException}; unavailable → {@link LedgerUnavailableException}.
   */
  AccountCreation createAccount(LedgerAccount account);

  /**
   * {@code POST /internal/v1/fundings}: issuance → wallet, single transfer (code 1). {@code fundingId} is deterministic
   * (low 8 bits zero), so a retry is a no-op. Throws {@link LedgerAccountNotFoundException},
   * {@link LedgerConflictException} or {@link LedgerUnavailableException}.
   */
  void fund(UUID fundingId, UUID accountId, long amount);
}
