package com.bracits.transactionservice.domain.wallet.model;

import com.bracits.transactionservice.domain.wallet.enums.WalletStatus;
import com.bracits.transactionservice.domain.wallet.enums.WalletType;
import java.util.UUID;

/**
 * A wallet to register (test profile). {@code ledgerAccountId} is assigned by this service (spec
 * 6.4).
 */
public record NewWallet(
    String msisdn,
    String holderName,
    WalletType type,
    WalletStatus status,
    int kycTier,
    UUID ledgerAccountId) {

}
