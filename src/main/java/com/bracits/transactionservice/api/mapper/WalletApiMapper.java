package com.bracits.transactionservice.api.mapper;

import com.bracits.transactionservice.api.dto.request.FundWalletRequest;
import com.bracits.transactionservice.api.dto.request.RegisterWalletRequest;
import com.bracits.transactionservice.api.dto.response.BalanceResponse;
import com.bracits.transactionservice.api.dto.response.FundWalletResponse;
import com.bracits.transactionservice.api.dto.response.RegisterWalletResponse;
import com.bracits.transactionservice.application.command.FundWalletCommand;
import com.bracits.transactionservice.application.command.RegisterWalletCommand;
import com.bracits.transactionservice.application.result.FundingResult;
import com.bracits.transactionservice.domain.ledger.model.AccountBalance;
import com.bracits.transactionservice.domain.wallet.model.Wallet;

/**
 * Wallet support DTOs ↔ use-case commands and results (decision B13 for the balance).
 */
public interface WalletApiMapper {

  RegisterWalletCommand toCommand(RegisterWalletRequest request);

  RegisterWalletResponse toResponse(Wallet wallet);

  FundWalletCommand toCommand(String msisdn, String idempotencyKey,
      FundWalletRequest request);

  FundWalletResponse toResponse(FundingResult.Funded funded);

  BalanceResponse toResponse(AccountBalance balance);
}
