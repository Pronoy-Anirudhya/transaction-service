package com.bracits.transactionservice.config;

import com.bracits.transactionservice.domain.ledger.SystemAccounts;
import org.springframework.stereotype.Component;

/** Maps the configured system account IDs ({@code poc.ledger.accounts.*}) to the domain value. */
@Component
public final class SystemAccountsMapper {

  public SystemAccounts toSystemAccounts(LedgerProperties.Accounts accounts) {
    return new SystemAccounts(
        accounts.feeIncome(),
        accounts.vatPayable(),
        accounts.commissionPayable(),
        accounts.issuance());
  }
}
