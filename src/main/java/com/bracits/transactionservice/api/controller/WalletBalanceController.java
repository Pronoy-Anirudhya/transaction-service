package com.bracits.transactionservice.api.controller;

import com.bracits.transactionservice.api.ApiConstants;
import com.bracits.transactionservice.api.dto.BalanceResponse;
import com.bracits.transactionservice.api.error.ApiErrorCode;
import com.bracits.transactionservice.api.error.ApiException;
import com.bracits.transactionservice.api.error.ApiMessages;
import com.bracits.transactionservice.api.mapper.WalletApiMapper;
import com.bracits.transactionservice.application.WalletSupportService;
import com.bracits.transactionservice.application.result.WalletLookupResult;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Balance read through to the ledger (spec 7.1). Balances are never stored in PostgreSQL. */
@RestController
@RequestMapping(ApiConstants.WALLETS_PATH)
public class WalletBalanceController {

  private final WalletSupportService wallets;
  private final WalletApiMapper mapper;

  public WalletBalanceController(WalletSupportService wallets, WalletApiMapper mapper) {
    this.wallets = wallets;
    this.mapper = mapper;
  }

  @GetMapping(ApiConstants.WALLET_BALANCE_PATH)
  public BalanceResponse balance(
      @PathVariable(ApiConstants.MSISDN_VARIABLE) @Pattern(regexp = ApiConstants.MSISDN_REGEX) String msisdn) {
    return switch (wallets.balance(msisdn)) {
      case WalletLookupResult.BalanceFound found -> mapper.toResponse(found.balance());
      case WalletLookupResult.WalletNotFound notFound ->
          throw new ApiException(ApiErrorCode.NOT_FOUND, ApiMessages.WALLET_NOT_FOUND);
    };
  }
}
