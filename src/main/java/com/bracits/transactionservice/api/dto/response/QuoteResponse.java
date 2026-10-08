package com.bracits.transactionservice.api.dto.response;

import java.time.Instant;

/**
 * 200 body of the quote endpoint (FR-01). {@code receiverName} is masked.
 */
public record QuoteResponse(
    String receiverName,
    long amount,
    long fee,
    long vat,
    long commission,
    long totalDebit,
    String quoteToken,
    Instant expiresAt) {

}
