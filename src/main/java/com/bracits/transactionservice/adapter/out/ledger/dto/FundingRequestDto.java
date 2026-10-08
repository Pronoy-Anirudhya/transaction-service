package com.bracits.transactionservice.adapter.out.ledger.dto;

/**
 * {@code POST /internal/v1/fundings} body: {@code {fundingId, accountId, amount}}.
 */
public record FundingRequestDto(String fundingId, String accountId, long amount) {

}
