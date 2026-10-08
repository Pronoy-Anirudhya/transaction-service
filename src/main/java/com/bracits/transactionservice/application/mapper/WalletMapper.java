package com.bracits.transactionservice.application.mapper;

import com.bracits.transactionservice.application.command.RegisterWalletCommand;
import com.bracits.transactionservice.domain.ledger.LedgerAccount;
import com.bracits.transactionservice.domain.ledger.LedgerAccountCode;
import com.bracits.transactionservice.domain.ledger.LedgerAccountFlag;
import com.bracits.transactionservice.domain.wallet.NewWallet;
import com.bracits.transactionservice.domain.wallet.Wallet;
import com.bracits.transactionservice.domain.wallet.WalletStatus;
import com.bracits.transactionservice.domain.wallet.WalletType;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/** Conversions for test-profile wallet registration: command → new wallet, wallet → ledger account (spec 6.2). */
@Component
public final class WalletMapper {

  /** Registered wallets are ACTIVE customer wallets. */
  public NewWallet toNewWallet(RegisterWalletCommand command, UUID ledgerAccountId) {
    return new NewWallet(
        command.msisdn(),
        command.holderName(),
        WalletType.CUSTOMER,
        WalletStatus.ACTIVE,
        command.kycTier(),
        ledgerAccountId);
  }

  /** Customer wallet account: code 100, debits must not exceed credits, {@code user_data_64} = wallet_id. */
  public LedgerAccount toLedgerAccount(Wallet wallet) {
    return new LedgerAccount(
        wallet.ledgerAccountId(),
        LedgerAccountCode.CUSTOMER_WALLET,
        Set.of(LedgerAccountFlag.DEBITS_MUST_NOT_EXCEED_CREDITS),
        wallet.walletId());
  }
}
