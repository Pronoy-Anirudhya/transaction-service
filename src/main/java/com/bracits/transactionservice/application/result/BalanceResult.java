package com.bracits.transactionservice.application.result;

import com.bracits.transactionservice.domain.ledger.model.AccountBalance;

/**
 * Outcome of the balance read-through use case.
 */
public sealed interface BalanceResult {

  /**
   * 200: the ledger balance.
   */
  record BalanceFound(AccountBalance balance) implements BalanceResult {

  }

  /**
   * 404: no wallet with that MSISDN (or the ledger does not know its account).
   */
  record WalletNotFound() implements BalanceResult {

  }
}
