package com.bracits.transactionservice.config.mapper.impl;

import com.bracits.transactionservice.config.mapper.SystemAccountsMapper;
import com.bracits.transactionservice.config.properties.LedgerProperties;
import com.bracits.transactionservice.domain.ledger.model.SystemAccounts;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link SystemAccountsMapper}.
 */
@Component
public final class SystemAccountsMapperImpl implements SystemAccountsMapper {

  @Override
  public SystemAccounts toSystemAccounts(LedgerProperties.Accounts accounts) {
    return new SystemAccounts(
        accounts.feeIncome(),
        accounts.vatPayable(),
        accounts.commissionPayable(),
        accounts.issuance());
  }
}
