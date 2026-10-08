package com.bracits.transactionservice.api.dto.response;

import com.bracits.transactionservice.api.enums.ApiTxnStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code GET /api/v1/send-money/{txnId}}: the current state (FR-04).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TxnStatusResponse(
    UUID txnId,
    ApiTxnStatus status,
    long amount,
    long fee,
    long vat,
    long commission,
    long totalDebit,
    String currency,
    String reference,
    String failureCode,
    Instant createdAt,
    Instant completedAt,
    String message) {

}
