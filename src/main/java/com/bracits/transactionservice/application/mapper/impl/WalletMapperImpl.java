package com.bracits.transactionservice.application.mapper.impl;

import com.bracits.transactionservice.application.command.RegisterWalletCommand;
import com.bracits.transactionservice.application.mapper.WalletMapper;
import com.bracits.transactionservice.domain.ledger.enums.LedgerAccountCode;
import com.bracits.transactionservice.domain.ledger.enums.LedgerAccountFlag;
import com.bracits.transactionservice.domain.ledger.model.LedgerAccount;
import com.bracits.transactionservice.domain.wallet.enums.WalletStatus;
import com.bracits.transactionservice.domain.wallet.enums.WalletType;
import com.bracits.transactionservice.domain.wallet.model.NewWallet;
import com.bracits.transactionservice.domain.wallet.model.Wallet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link WalletMapper}.
 */
@Component
public final class WalletMapperImpl implements WalletMapper {

  /**
   * Registered wallets are ACTIVE customer wallets.
   */
  @Override
  public NewWallet toNewWallet(RegisterWalletCommand command, UUID ledgerAccountId) {
    return new NewWallet(
        command.msisdn(),
        command.holderName(),
        WalletType.CUSTOMER,
        WalletStatus.ACTIVE,
        command.kycTier(),
        ledgerAccountId);
  }

  /**
   * Customer wallet account: code 100, debits must not exceed credits, {@code user_data_64} =
   * wallet_id.
   */
  @Override
  public LedgerAccount toLedgerAccount(Wallet wallet) {
    return new LedgerAccount(
        wallet.ledgerAccountId(),
        LedgerAccountCode.CUSTOMER_WALLET,
        Set.of(LedgerAccountFlag.DEBITS_MUST_NOT_EXCEED_CREDITS),
        wallet.walletId());
  }
}
