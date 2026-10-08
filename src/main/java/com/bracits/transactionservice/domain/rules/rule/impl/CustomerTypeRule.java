package com.bracits.transactionservice.domain.rules.rule.impl;

import com.bracits.transactionservice.domain.enums.FailureCode;
import com.bracits.transactionservice.domain.rules.model.SendMoneyContext;
import com.bracits.transactionservice.domain.rules.rule.SendMoneyRule;
import com.bracits.transactionservice.domain.wallet.model.Wallet;
import java.util.Optional;

/**
 * BR-01: both wallets are of type CUSTOMER. A non-customer sender is reported as
 * {@code WALLET_NOT_FOUND}, a non-customer receiver as {@code RECEIVER_NOT_ALLOWED} (decision B14).
 * Missing wallets are skipped.
 */
public final class CustomerTypeRule implements SendMoneyRule {

  @Override
  public Optional<FailureCode> check(SendMoneyContext ctx) {
    if (isNotCustomer(ctx.sender())) {
      return Optional.of(FailureCode.WALLET_NOT_FOUND);
    }
    if (isNotCustomer(ctx.receiver())) {
      return Optional.of(FailureCode.RECEIVER_NOT_ALLOWED);
    }
    return Optional.empty();
  }

  private static boolean isNotCustomer(Optional<Wallet> wallet) {
    return wallet.map(w -> !w.isCustomer()).orElse(false);
  }
}
