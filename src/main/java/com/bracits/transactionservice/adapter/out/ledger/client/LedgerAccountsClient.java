package com.bracits.transactionservice.adapter.out.ledger.client;

import com.bracits.transactionservice.adapter.out.ledger.constant.LedgerApiConstants;
import com.bracits.transactionservice.adapter.out.ledger.dto.LedgerProblemDto;
import com.bracits.transactionservice.adapter.out.ledger.http.LedgerHttpExecutor;
import com.bracits.transactionservice.adapter.out.ledger.mapper.LedgerDtoMapper;
import com.bracits.transactionservice.adapter.out.ledger.model.LedgerHttpResponse;
import com.bracits.transactionservice.domain.enums.FailureCode;
import com.bracits.transactionservice.domain.ledger.enums.AccountCreation;
import com.bracits.transactionservice.domain.ledger.model.LedgerAccount;
import com.bracits.transactionservice.port.out.client.LedgerAccountsPort;
import com.bracits.transactionservice.port.out.exception.LedgerAccountNotFoundException;
import com.bracits.transactionservice.port.out.exception.LedgerConflictException;
import com.bracits.transactionservice.port.out.exception.LedgerUnavailableException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * {@link LedgerAccountsPort} over HTTP: idempotent account creation and test-profile funding.
 * Failures surface as the port exceptions.
 */
@Component
public final class LedgerAccountsClient implements LedgerAccountsPort {

  private final LedgerHttpExecutor http;
  private final LedgerDtoMapper mapper;

  public LedgerAccountsClient(LedgerHttpExecutor http, LedgerDtoMapper mapper) {
    this.http = http;
    this.mapper = mapper;
  }

  @Override
  public AccountCreation createAccount(LedgerAccount account) {
    byte[] body = http.toJsonOrThrow(mapper.toAccountRequestDto(account));

    LedgerHttpResponse response = http.postJsonOrThrow(LedgerApiConstants.ACCOUNTS_PATH, body);

    if (response.is(HttpStatus.CREATED) || response.is(HttpStatus.OK)) {
      return mapper.toAccountCreation(response.is(HttpStatus.CREATED));
    }

    if (response.is(HttpStatus.CONFLICT)) {
      throw new LedgerConflictException(
          LedgerApiConstants.MSG_ACCOUNT_CONFLICT.formatted(mapper.toWireId(account.accountId())));
    }

    throw LedgerHttpExecutor.unexpectedStatus(response, LedgerApiConstants.ACCOUNTS_PATH);
  }

  @Override
  public void fund(UUID fundingId, UUID accountId, long amount) {
    byte[] body = http.toJsonOrThrow(mapper.toFundingRequestDto(fundingId, accountId, amount));

    LedgerHttpResponse response = http.postJsonOrThrow(LedgerApiConstants.FUNDINGS_PATH, body);

    if (response.is(HttpStatus.OK)) {
      return;
    }

    if (response.is(HttpStatus.UNPROCESSABLE_CONTENT)) {
      throw rejectedFunding(response, fundingId, accountId);
    }

    if (response.is(HttpStatus.CONFLICT)) {
      throw new LedgerConflictException(
          LedgerApiConstants.MSG_FUNDING_CONFLICT.formatted(mapper.toWireId(fundingId)));
    }

    throw LedgerHttpExecutor.unexpectedStatus(response, LedgerApiConstants.FUNDINGS_PATH);
  }

  /**
   * 422 on a funding: an unknown account is reported as such; any other rejection as unavailable
   * (decision L4).
   */
  private RuntimeException rejectedFunding(LedgerHttpResponse response, UUID fundingId,
      UUID accountId) {
    String code = http.read(response, LedgerProblemDto.class).map(LedgerProblemDto::code)
        .orElse(null);

    if (mapper.toFailureCode(code) == FailureCode.WALLET_NOT_FOUND) {
      return new LedgerAccountNotFoundException(
          LedgerApiConstants.MSG_ACCOUNT_NOT_FOUND.formatted(mapper.toWireId(accountId)));
    }

    return new LedgerUnavailableException(
        LedgerApiConstants.MSG_FUNDING_REJECTED.formatted(mapper.toWireId(fundingId), code));
  }
}
