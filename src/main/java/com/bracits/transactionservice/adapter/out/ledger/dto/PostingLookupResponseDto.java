package com.bracits.transactionservice.adapter.out.ledger.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code PostingLookupResponse} of {@code GET /internal/v1/postings/{postingId}?legs=n}:
 * {@code {postingId, status: POSTED | NOT_FOUND, timestamp?}}; {@code timestamp} is present only
 * when POSTED.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PostingLookupResponseDto(String postingId, String status, Long timestamp) {

}
