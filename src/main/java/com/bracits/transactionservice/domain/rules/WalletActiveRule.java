package com.bracits.transactionservice.domain.rules;

import com.bracits.transactionservice.domain.FailureCode;
import com.bracits.transactionservice.domain.wallet.Wallet;

import java.util.Optional;

/** BR-01: both wallets are ACTIVE. A missing wallet is not this rule's concern ({@link WalletExistsRule}). */
public final class WalletActiveRule implements SendMoneyRule {

  @Override
  public Optional<FailureCode> check(SendMoneyContext ctx) {
    return isInactive(ctx.sender()) || isInactive(ctx.receiver())
        ? Optional.of(FailureCode.WALLET_INACTIVE)
        : Optional.empty();
  }

  private static boolean isInactive(Optional<Wallet> wallet) {
    return wallet.map(w -> !w.isActive()).orElse(false);
  }
}
