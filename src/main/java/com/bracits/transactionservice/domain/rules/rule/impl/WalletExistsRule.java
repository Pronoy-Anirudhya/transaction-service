package com.bracits.transactionservice.domain.rules.rule.impl;

import com.bracits.transactionservice.domain.enums.FailureCode;
import com.bracits.transactionservice.domain.rules.model.SendMoneyContext;
import com.bracits.transactionservice.domain.rules.rule.SendMoneyRule;
import java.util.Optional;

/**
 * BR-01: both wallets exist.
 */
public final class WalletExistsRule implements SendMoneyRule {

  @Override
  public Optional<FailureCode> check(SendMoneyContext ctx) {
    return ctx.sender().isEmpty() || ctx.receiver().isEmpty()
        ? Optional.of(FailureCode.WALLET_NOT_FOUND)
        : Optional.empty();
  }
}
