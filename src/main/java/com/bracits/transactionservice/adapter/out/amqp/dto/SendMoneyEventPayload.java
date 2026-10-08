package com.bracits.transactionservice.adapter.out.amqp.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * JSON body of {@code SendMoneyCompleted} / {@code SendMoneyFailed}, schema version 1 (spec 9).
 * Field order is the wire order. {@code ledgerTimestamp} and {@code failureCode} are written as
 * {@code null} when absent, never omitted.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SendMoneyEventPayload(
    UUID eventId,
    String eventType,
    int schemaVersion,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant occurredAt,
    UUID txnId,
    long senderWalletId,
    long receiverWalletId,
    long amount,
    long fee,
    long vat,
    long commission,
    long feeIncome,
    String currency,
    Long ledgerTimestamp,
    String failureCode) {

}
