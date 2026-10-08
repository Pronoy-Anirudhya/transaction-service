package com.bracits.transactionservice.adapter.out.ledger.mapper;

import com.bracits.transactionservice.adapter.out.ledger.dto.AccountRequestDto;
import com.bracits.transactionservice.adapter.out.ledger.dto.BalanceResponseDto;
import com.bracits.transactionservice.adapter.out.ledger.dto.FundingRequestDto;
import com.bracits.transactionservice.adapter.out.ledger.dto.LedgerProblemDto;
import com.bracits.transactionservice.adapter.out.ledger.dto.LegDto;
import com.bracits.transactionservice.adapter.out.ledger.dto.PostingLookupResponseDto;
import com.bracits.transactionservice.adapter.out.ledger.dto.PostingRequestDto;
import com.bracits.transactionservice.adapter.out.ledger.dto.PostingResponseDto;
import com.bracits.transactionservice.domain.enums.FailureCode;
import com.bracits.transactionservice.domain.ledger.enums.AccountCreation;
import com.bracits.transactionservice.domain.ledger.model.AccountBalance;
import com.bracits.transactionservice.domain.ledger.model.LedgerAccount;
import com.bracits.transactionservice.domain.ledger.model.Leg;
import com.bracits.transactionservice.domain.ledger.model.PostingLookup;
import com.bracits.transactionservice.domain.ledger.model.PostingOutcome.Posted;
import com.bracits.transactionservice.domain.ledger.model.PostingOutcome.Rejected;
import com.bracits.transactionservice.domain.ledger.model.PostingOutcome;
import com.bracits.transactionservice.domain.ledger.model.PostingRequest;
import java.util.Optional;
import java.util.UUID;

/**
 * Every conversion between the domain ledger model and the ledger-service wire DTOs (spec 7.2).
 */
public interface LedgerDtoMapper {

  /**
   * Posting request → wire body. Legs keep their order, so the leg index on the wire is the list
   * index + 1.
   */
  PostingRequestDto toPostingRequestDto(PostingRequest request);

  LegDto toLegDto(Leg leg);

  /**
   * Account → wire body; flags sorted by declaration order so the body is deterministic.
   */
  AccountRequestDto toAccountRequestDto(LedgerAccount account);

  FundingRequestDto toFundingRequestDto(UUID fundingId, UUID accountId, long amount);

  /**
   * 200 answer → {@link Posted}; empty unless the body says {@code POSTED} and carries a
   * timestamp.
   */
  Optional<PostingOutcome> toPosted(PostingResponseDto body);

  /**
   * 422 problem → {@link Rejected} with the mapped failure code and the 1-based root-cause leg (0
   * if absent). The HTTP status alone makes it definitive; {@code postingStatus} is not consulted.
   */
  PostingOutcome toRejected(LedgerProblemDto problem);

  /**
   * Ledger 422 code → business failure code (decision B5). {@code PREVIOUSLY_REJECTED} (an earlier
   * attempt was rejected; the original reason is not repeated) and any unknown code →
   * {@code LEDGER_REJECTED}.
   */
  FailureCode toFailureCode(String ledgerCode);

  /**
   * Lookup answer → {@link PostingLookup}; empty for a status this client does not know. The
   * timestamp is kept only for POSTED, and is empty if the ledger omitted it.
   */
  Optional<PostingLookup> toPostingLookup(PostingLookupResponseDto body);

  AccountBalance toAccountBalance(BalanceResponseDto body);

  /**
   * 201 → CREATED, 200 → ALREADY_EXISTS.
   */
  AccountCreation toAccountCreation(boolean created);

  /**
   * Posting outcome → {@code outcome} tag value of {@code ledger.posting.duration}.
   */
  String toMetricOutcome(PostingOutcome outcome);

  /**
   * UUIDs travel as canonical lower-case strings.
   */
  String toWireId(UUID id);
}
