package com.bracits.transactionservice.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/** 200 COMPLETED or 202 PROCESSING body of {@code POST /api/v1/send-money}. {@code completedAt} is null while PROCESSING. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SendMoneyResponse(
    UUID txnId,
    ApiTxnStatus status,
    long amount,
    long fee,
    long vat,
    long commission,
    long totalDebit,
    Instant completedAt) {
}
