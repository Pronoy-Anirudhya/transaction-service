package com.bracits.transactionservice.application.mapper;

import com.bracits.transactionservice.application.command.RegisterWalletCommand;
import com.bracits.transactionservice.domain.ledger.model.LedgerAccount;
import com.bracits.transactionservice.domain.wallet.model.NewWallet;
import com.bracits.transactionservice.domain.wallet.model.Wallet;
import java.util.UUID;

/**
 * Conversions for test-profile wallet registration: command → new wallet, wallet → ledger account
 * (spec 6.2).
 */
public interface WalletMapper {

  /**
   * Registered wallets are ACTIVE customer wallets.
   */
  NewWallet toNewWallet(RegisterWalletCommand command, UUID ledgerAccountId);

  /**
   * Customer wallet account: code 100, debits must not exceed credits, {@code user_data_64} =
   * wallet_id.
   */
  LedgerAccount toLedgerAccount(Wallet wallet);
}
