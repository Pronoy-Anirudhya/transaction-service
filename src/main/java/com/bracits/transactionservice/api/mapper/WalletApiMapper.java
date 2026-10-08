package com.bracits.transactionservice.api.mapper;

import com.bracits.transactionservice.api.ApiConstants;
import com.bracits.transactionservice.api.dto.BalanceResponse;
import com.bracits.transactionservice.api.dto.FundWalletRequest;
import com.bracits.transactionservice.api.dto.FundWalletResponse;
import com.bracits.transactionservice.api.dto.RegisterWalletRequest;
import com.bracits.transactionservice.api.dto.RegisterWalletResponse;
import com.bracits.transactionservice.application.command.FundWalletCommand;
import com.bracits.transactionservice.application.command.RegisterWalletCommand;
import com.bracits.transactionservice.application.result.WalletLookupResult;
import com.bracits.transactionservice.domain.ledger.AccountBalance;
import com.bracits.transactionservice.domain.wallet.Wallet;
import org.springframework.stereotype.Component;

/** Wallet support DTOs ↔ use-case commands and results (decision B13 for the balance). */
@Component
public final class WalletApiMapper {

  public RegisterWalletCommand toCommand(RegisterWalletRequest request) {
    return new RegisterWalletCommand(request.msisdn(), request.holderName(), request.kycTier());
  }

  public RegisterWalletResponse toResponse(Wallet wallet) {
    return new RegisterWalletResponse(wallet.walletId(), wallet.msisdn(), wallet.ledgerAccountId());
  }

  public FundWalletCommand toCommand(String msisdn, String idempotencyKey, FundWalletRequest request) {
    return new FundWalletCommand(msisdn, idempotencyKey, request.amount());
  }

  public FundWalletResponse toResponse(WalletLookupResult.Funded funded) {
    return new FundWalletResponse(
        funded.fundingId(), funded.msisdn(), funded.amount(), ApiConstants.FUNDING_STATUS_POSTED);
  }

  public BalanceResponse toResponse(AccountBalance balance) {
    return new BalanceResponse(balance.netPosted(), balance.netPending(), balance.available());
  }
}
