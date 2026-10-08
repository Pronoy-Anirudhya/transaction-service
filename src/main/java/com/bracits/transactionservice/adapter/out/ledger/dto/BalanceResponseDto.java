package com.bracits.transactionservice.adapter.out.ledger.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code BalanceResponse} of {@code GET /internal/v1/accounts/{id}/balance}. All amounts in poisha;
 * {@code available = creditsPosted - debitsPosted - debitsPending} and may be negative.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BalanceResponseDto(
    String accountId,
    long debitsPosted,
    long creditsPosted,
    long debitsPending,
    long creditsPending,
    long available) {

}
