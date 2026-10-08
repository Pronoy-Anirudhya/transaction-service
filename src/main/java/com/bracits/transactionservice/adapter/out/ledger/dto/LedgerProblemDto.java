package com.bracits.transactionservice.adapter.out.ledger.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The members of a ledger RFC 9457 problem ({@code application/problem+json}) that this client reads: {@code code},
 * {@code postingStatus} (REJECTED | UNKNOWN) and {@code legIndex} (1-based). The numeric {@code status} and the other
 * standard members ({@code type}, {@code title}, {@code detail}, {@code instance}, …) are deliberately not mapped:
 * the HTTP status line is authoritative.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LedgerProblemDto(String code, String postingStatus, Integer legIndex) {
}
