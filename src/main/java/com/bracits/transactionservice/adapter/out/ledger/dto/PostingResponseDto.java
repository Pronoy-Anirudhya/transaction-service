package com.bracits.transactionservice.adapter.out.ledger.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code PostingResponse}: the 200 answer of {@code POST /internal/v1/postings} and {@code POST /internal/v1/fundings},
 * {@code {postingId, status: POSTED, replay, timestamp}}. Error answers are {@link LedgerProblemDto}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PostingResponseDto(String postingId, String status, Boolean replay, Long timestamp) {
}
