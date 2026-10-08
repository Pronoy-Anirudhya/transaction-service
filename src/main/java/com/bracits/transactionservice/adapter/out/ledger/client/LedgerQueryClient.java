package com.bracits.transactionservice.adapter.out.ledger.client;

import com.bracits.transactionservice.adapter.out.ledger.constant.LedgerApiConstants;
import com.bracits.transactionservice.adapter.out.ledger.dto.BalanceResponseDto;
import com.bracits.transactionservice.adapter.out.ledger.dto.PostingLookupResponseDto;
import com.bracits.transactionservice.adapter.out.ledger.http.LedgerHttpExecutor;
import com.bracits.transactionservice.adapter.out.ledger.mapper.LedgerDtoMapper;
import com.bracits.transactionservice.adapter.out.ledger.model.LedgerHttpResponse;
import com.bracits.transactionservice.domain.ledger.model.AccountBalance;
import com.bracits.transactionservice.domain.ledger.model.PostingLookup;
import com.bracits.transactionservice.port.out.client.LedgerQueryPort;
import com.bracits.transactionservice.port.out.exception.LedgerAccountNotFoundException;
import com.bracits.transactionservice.port.out.exception.LedgerUnavailableException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * {@link LedgerQueryPort} over HTTP: posting look-up and account balance. Failures surface as the
 * port exceptions.
 */
@Component
public final class LedgerQueryClient implements LedgerQueryPort {

  private final LedgerHttpExecutor http;
  private final LedgerDtoMapper mapper;

  public LedgerQueryClient(LedgerHttpExecutor http, LedgerDtoMapper mapper) {
    this.http = http;
    this.mapper = mapper;
  }

  @Override
  public PostingLookup lookupPosting(UUID postingId, int legCount) {
    requireValidLegCount(legCount);

    String wireId = mapper.toWireId(postingId);

    LedgerHttpResponse response = http.getOrThrow(uriBuilder -> uriBuilder
        .path(LedgerApiConstants.POSTING_PATH)
        .queryParam(LedgerApiConstants.QUERY_LEGS, legCount)
        .build(wireId));
    if (!response.is(HttpStatus.OK)) {
      throw LedgerHttpExecutor.unexpectedStatus(response, LedgerApiConstants.POSTING_PATH);
    }

    PostingLookupResponseDto body = http.read(response, PostingLookupResponseDto.class)
        .orElseThrow(
            () -> LedgerHttpExecutor.unparsableBody(response, LedgerApiConstants.POSTING_PATH));

    return mapper.toPostingLookup(body)
        .orElseThrow(() -> new LedgerUnavailableException(
            LedgerApiConstants.MSG_UNKNOWN_LOOKUP_STATUS.formatted(body.status(), wireId)));
  }

  @Override
  public AccountBalance balance(UUID accountId) {
    String wireId = mapper.toWireId(accountId);

    LedgerHttpResponse response = http.getOrThrow(uriBuilder -> uriBuilder
        .path(LedgerApiConstants.ACCOUNT_BALANCE_PATH)
        .build(wireId));

    if (response.is(HttpStatus.OK)) {
      return http.read(response, BalanceResponseDto.class)
          .map(mapper::toAccountBalance)
          .orElseThrow(() -> LedgerHttpExecutor.unparsableBody(response,
              LedgerApiConstants.ACCOUNT_BALANCE_PATH));
    }

    if (response.is(HttpStatus.NOT_FOUND)) {
      throw new LedgerAccountNotFoundException(
          LedgerApiConstants.MSG_ACCOUNT_NOT_FOUND.formatted(wireId));
    }

    throw LedgerHttpExecutor.unexpectedStatus(response, LedgerApiConstants.ACCOUNT_BALANCE_PATH);
  }

  private static void requireValidLegCount(int legCount) {
    if (legCount < LedgerApiConstants.MIN_LEGS || legCount > LedgerApiConstants.MAX_LEGS) {
      throw new IllegalArgumentException(LedgerApiConstants.MSG_INVALID_LEG_COUNT
          .formatted(LedgerApiConstants.MIN_LEGS, LedgerApiConstants.MAX_LEGS, legCount));
    }
  }
}
