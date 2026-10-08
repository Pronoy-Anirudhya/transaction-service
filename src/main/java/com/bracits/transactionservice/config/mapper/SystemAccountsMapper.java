package com.bracits.transactionservice.config.mapper;

import com.bracits.transactionservice.config.properties.LedgerProperties;
import com.bracits.transactionservice.domain.ledger.model.SystemAccounts;

/**
 * Maps the configured system account IDs ({@code poc.ledger.accounts.*}) to the domain value.
 */
public interface SystemAccountsMapper {

  SystemAccounts toSystemAccounts(LedgerProperties.Accounts accounts);
}
