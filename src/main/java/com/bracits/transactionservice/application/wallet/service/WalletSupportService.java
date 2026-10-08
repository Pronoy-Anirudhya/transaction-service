package com.bracits.transactionservice.application.wallet.service;

import com.bracits.transactionservice.application.command.FundWalletCommand;
import com.bracits.transactionservice.application.command.RegisterWalletCommand;
import com.bracits.transactionservice.application.result.BalanceResult;
import com.bracits.transactionservice.application.result.FundingResult;
import com.bracits.transactionservice.application.result.RegisterWalletResult;

/**
 * FR-09 support use cases: register a wallet, fund it from the issuance account, and read its
 * balance through to the ledger. All idempotent (decisions B11, B12).
 */
public interface WalletSupportService {

  /**
   * Inserts the wallet (PostgreSQL assigns nothing but the ID; the ledger account ID is
   * time-ordered, spec 6.4), then creates its ledger account. A replay with identical fields
   * re-ensures the ledger account.
   */
  RegisterWalletResult register(RegisterWalletCommand command);

  FundingResult fund(FundWalletCommand command);

  BalanceResult balance(String msisdn);
}
