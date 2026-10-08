package com.bracits.transactionservice.domain.wallet;

import java.util.UUID;

/** A wallet as stored in {@code wallet}. Balances live only in the ledger. */
public record Wallet(
    long walletId,
    String msisdn,
    String holderName,
    WalletType type,
    WalletStatus status,
    int kycTier,
    UUID ledgerAccountId) {

  public boolean isActive() {
    return status == WalletStatus.ACTIVE;
  }

  public boolean isCustomer() {
    return type == WalletType.CUSTOMER;
  }
}
