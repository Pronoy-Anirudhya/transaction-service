package com.bracits.transactionservice.adapter.out.ledger.mapper.impl;

import com.bracits.transactionservice.adapter.out.ledger.constant.LedgerApiConstants;
import com.bracits.transactionservice.adapter.out.ledger.dto.AccountRequestDto;
import com.bracits.transactionservice.adapter.out.ledger.dto.BalanceResponseDto;
import com.bracits.transactionservice.adapter.out.ledger.dto.FundingRequestDto;
import com.bracits.transactionservice.adapter.out.ledger.dto.LedgerProblemDto;
import com.bracits.transactionservice.adapter.out.ledger.dto.LegDto;
import com.bracits.transactionservice.adapter.out.ledger.dto.PostingLookupResponseDto;
import com.bracits.transactionservice.adapter.out.ledger.dto.PostingRequestDto;
import com.bracits.transactionservice.adapter.out.ledger.dto.PostingResponseDto;
import com.bracits.transactionservice.adapter.out.ledger.mapper.LedgerDtoMapper;
import com.bracits.transactionservice.domain.enums.FailureCode;
import com.bracits.transactionservice.domain.ledger.enums.AccountCreation;
import com.bracits.transactionservice.domain.ledger.enums.LedgerAccountFlag;
import com.bracits.transactionservice.domain.ledger.enums.PostingLookupStatus;
import com.bracits.transactionservice.domain.ledger.model.AccountBalance;
import com.bracits.transactionservice.domain.ledger.model.LedgerAccount;
import com.bracits.transactionservice.domain.ledger.model.Leg;
import com.bracits.transactionservice.domain.ledger.model.PostingLookup;
import com.bracits.transactionservice.domain.ledger.model.PostingOutcome.Posted;
import com.bracits.transactionservice.domain.ledger.model.PostingOutcome.Rejected;
import com.bracits.transactionservice.domain.ledger.model.PostingOutcome.Unknown;
import com.bracits.transactionservice.domain.ledger.model.PostingOutcome;
import com.bracits.transactionservice.domain.ledger.model.PostingRequest;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link LedgerDtoMapper}.
 */
@Component
public final class LedgerDtoMapperImpl implements LedgerDtoMapper {

  /**
   * Posting request → wire body. Legs keep their order, so the leg index on the wire is the list
   * index + 1.
   */
  @Override
  public PostingRequestDto toPostingRequestDto(PostingRequest request) {
    List<LegDto> legs = request.legs().stream().map(this::toLegDto).toList();
    return new PostingRequestDto(
        toWireId(request.postingId()), request.product().code(), request.userData64(), legs);
  }

  @Override
  public LegDto toLegDto(Leg leg) {
    return new LegDto(
        toWireId(leg.debitAccountId()), toWireId(leg.creditAccountId()), leg.amount(),
        leg.code().code());
  }

  /**
   * Account → wire body; flags sorted by declaration order so the body is deterministic.
   */
  @Override
  public AccountRequestDto toAccountRequestDto(LedgerAccount account) {
    List<String> flags = account.flags().stream()
        .sorted(Comparator.naturalOrder())
        .map(LedgerAccountFlag::name)
        .toList();

    return new AccountRequestDto(toWireId(account.accountId()), account.code().code(), flags,
        account.userData64());
  }

  @Override
  public FundingRequestDto toFundingRequestDto(UUID fundingId, UUID accountId, long amount) {
    return new FundingRequestDto(toWireId(fundingId), toWireId(accountId), amount);
  }

  /**
   * 200 answer → {@link Posted}; empty unless the body says {@code POSTED} and carries a
   * timestamp.
   */
  @Override
  public Optional<PostingOutcome> toPosted(PostingResponseDto body) {
    if (!LedgerApiConstants.STATUS_POSTED.equals(body.status()) || body.timestamp() == null) {
      return Optional.empty();
    }

    return Optional.of(new Posted(body.timestamp(), Boolean.TRUE.equals(body.replay())));
  }

  /**
   * 422 problem → {@link Rejected} with the mapped failure code and the 1-based root-cause leg (0
   * if absent). The HTTP status alone makes it definitive; {@code postingStatus} is not consulted.
   */
  @Override
  public PostingOutcome toRejected(LedgerProblemDto problem) {
    int legIndex = problem.legIndex() == null ? 0 : problem.legIndex();
    return new Rejected(toFailureCode(problem.code()), legIndex);
  }

  /**
   * Ledger 422 code → business failure code (decision B5). {@code PREVIOUSLY_REJECTED} (an earlier
   * attempt was rejected; the original reason is not repeated) and any unknown code →
   * {@code LEDGER_REJECTED}.
   */
  @Override
  public FailureCode toFailureCode(String ledgerCode) {
    if (LedgerApiConstants.CODE_INSUFFICIENT_FUNDS.equals(ledgerCode)) {
      return FailureCode.INSUFFICIENT_FUNDS;
    }
    if (LedgerApiConstants.CODE_ACCOUNT_NOT_FOUND.equals(ledgerCode)) {
      return FailureCode.WALLET_NOT_FOUND;
    }
    if (LedgerApiConstants.CODE_PREVIOUSLY_REJECTED.equals(ledgerCode)) {
      return FailureCode.LEDGER_REJECTED;
    }
    return FailureCode.LEDGER_REJECTED;
  }

  /**
   * Lookup answer → {@link PostingLookup}; empty for a status this client does not know. The
   * timestamp is kept only for POSTED, and is empty if the ledger omitted it.
   */
  @Override
  public Optional<PostingLookup> toPostingLookup(PostingLookupResponseDto body) {
    if (LedgerApiConstants.STATUS_POSTED.equals(body.status())) {
      OptionalLong timestamp =
          body.timestamp() == null ? OptionalLong.empty() : OptionalLong.of(body.timestamp());
      return Optional.of(new PostingLookup(PostingLookupStatus.POSTED, timestamp));
    }
    if (LedgerApiConstants.STATUS_NOT_FOUND.equals(body.status())) {
      return Optional.of(new PostingLookup(PostingLookupStatus.NOT_FOUND, OptionalLong.empty()));
    }
    return Optional.empty();
  }

  @Override
  public AccountBalance toAccountBalance(BalanceResponseDto body) {
    return new AccountBalance(
        body.debitsPosted(), body.creditsPosted(), body.debitsPending(), body.creditsPending(),
        body.available());
  }

  /**
   * 201 → CREATED, 200 → ALREADY_EXISTS.
   */
  @Override
  public AccountCreation toAccountCreation(boolean created) {
    return created ? AccountCreation.CREATED : AccountCreation.ALREADY_EXISTS;
  }

  /**
   * Posting outcome → {@code outcome} tag value of {@code ledger.posting.duration}.
   */
  @Override
  public String toMetricOutcome(PostingOutcome outcome) {
    return switch (outcome) {
      case Posted _ -> LedgerApiConstants.OUTCOME_POSTED;
      case Rejected _ -> LedgerApiConstants.OUTCOME_REJECTED;
      case Unknown unknown -> switch (unknown.reason()) {
        case LEDGER_TIMEOUT -> LedgerApiConstants.OUTCOME_TIMEOUT;
        case POSTING_CONFLICT -> LedgerApiConstants.OUTCOME_CONFLICT;
        case LEDGER_ERROR -> LedgerApiConstants.OUTCOME_ERROR;
        case OVERLOADED -> LedgerApiConstants.OUTCOME_OVERLOADED;
      };
    };
  }

  /**
   * UUIDs travel as canonical lower-case strings.
   */
  @Override
  public String toWireId(UUID id) {
    return id.toString();
  }
}
