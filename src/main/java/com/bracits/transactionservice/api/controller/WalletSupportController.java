package com.bracits.transactionservice.api.controller;

import com.bracits.transactionservice.api.ApiConstants;
import com.bracits.transactionservice.api.dto.FundWalletRequest;
import com.bracits.transactionservice.api.dto.RegisterWalletRequest;
import com.bracits.transactionservice.api.dto.RegisterWalletResponse;
import com.bracits.transactionservice.api.error.ApiErrorCode;
import com.bracits.transactionservice.api.error.ApiException;
import com.bracits.transactionservice.api.error.ApiMessages;
import com.bracits.transactionservice.api.mapper.WalletApiMapper;
import com.bracits.transactionservice.application.WalletSupportService;
import com.bracits.transactionservice.application.result.RegisterWalletResult;
import com.bracits.transactionservice.application.result.WalletLookupResult;
import com.bracits.transactionservice.config.PropertyConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** FR-09 test-profile support endpoints: register a wallet and fund it from the issuance account. */
@RestController
@Profile(PropertyConstants.PROFILE_TEST)
@RequestMapping(ApiConstants.WALLETS_PATH)
public class WalletSupportController {

  private final WalletSupportService wallets;
  private final WalletApiMapper mapper;

  public WalletSupportController(WalletSupportService wallets, WalletApiMapper mapper) {
    this.wallets = wallets;
    this.mapper = mapper;
  }

  /** 201 on first registration, 200 on an identical replay, 409 if the MSISDN exists with other details. */
  @PostMapping
  public ResponseEntity<RegisterWalletResponse> register(@Valid @RequestBody RegisterWalletRequest request) {
    return switch (wallets.register(mapper.toCommand(request))) {
      case RegisterWalletResult.Registered registered -> ResponseEntity
          .status(registered.created() ? HttpStatus.CREATED : HttpStatus.OK)
          .body(mapper.toResponse(registered.wallet()));
      case RegisterWalletResult.MsisdnExists exists ->
          throw new ApiException(ApiErrorCode.MSISDN_EXISTS, ApiMessages.MSISDN_EXISTS);
    };
  }

  @PostMapping(ApiConstants.WALLET_FUND_PATH)
  public ResponseEntity<?> fund(
      @PathVariable(ApiConstants.MSISDN_VARIABLE) @Pattern(regexp = ApiConstants.MSISDN_REGEX) String msisdn,
      @RequestHeader(ApiConstants.HEADER_IDEMPOTENCY_KEY)
      @NotBlank @Size(max = ApiConstants.IDEMPOTENCY_KEY_MAX_LENGTH) String idempotencyKey,
      @Valid @RequestBody FundWalletRequest request) {
    return switch (wallets.fund(mapper.toCommand(msisdn, idempotencyKey, request))) {
      case WalletLookupResult.Funded funded -> ResponseEntity.ok(mapper.toResponse(funded));
      case WalletLookupResult.WalletNotFound notFound ->
          throw new ApiException(ApiErrorCode.NOT_FOUND, ApiMessages.WALLET_NOT_FOUND);
    };
  }
}
