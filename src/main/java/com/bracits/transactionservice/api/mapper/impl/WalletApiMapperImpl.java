package com.bracits.transactionservice.api.mapper.impl;

import com.bracits.transactionservice.api.constant.ApiConstants;
import com.bracits.transactionservice.api.dto.request.FundWalletRequest;
import com.bracits.transactionservice.api.dto.request.RegisterWalletRequest;
import com.bracits.transactionservice.api.dto.response.BalanceResponse;
import com.bracits.transactionservice.api.dto.response.FundWalletResponse;
import com.bracits.transactionservice.api.dto.response.RegisterWalletResponse;
import com.bracits.transactionservice.api.mapper.WalletApiMapper;
import com.bracits.transactionservice.application.command.FundWalletCommand;
import com.bracits.transactionservice.application.command.RegisterWalletCommand;
import com.bracits.transactionservice.application.result.FundingResult;
import com.bracits.transactionservice.domain.ledger.model.AccountBalance;
import com.bracits.transactionservice.domain.wallet.model.Wallet;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link WalletApiMapper}.
 */
@Component
public final class WalletApiMapperImpl implements WalletApiMapper {

  @Override
  public RegisterWalletCommand toCommand(RegisterWalletRequest request) {
    return new RegisterWalletCommand(request.msisdn(), request.holderName(), request.kycTier());
  }

  @Override
  public RegisterWalletResponse toResponse(Wallet wallet) {
    return new RegisterWalletResponse(wallet.walletId(), wallet.msisdn(), wallet.ledgerAccountId());
  }

  @Override
  public FundWalletCommand toCommand(String msisdn, String idempotencyKey,
      FundWalletRequest request) {
    return new FundWalletCommand(msisdn, idempotencyKey, request.amount());
  }

  @Override
  public FundWalletResponse toResponse(FundingResult.Funded funded) {
    return new FundWalletResponse(
        funded.fundingId(), funded.msisdn(), funded.amount(), ApiConstants.FUNDING_STATUS_POSTED);
  }

  @Override
  public BalanceResponse toResponse(AccountBalance balance) {
    return new BalanceResponse(balance.netPosted(), balance.netPending(), balance.available());
  }
}
