package com.bracits.transactionservice.domain.event;

import com.bracits.transactionservice.domain.FailureCode;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * {@code SendMoneyCompleted} / {@code SendMoneyFailed} (spec 9). {@code eventId} is deterministic
 * ({@link EventIds#of}) and is also the AMQP {@code message_id}, so republishes can be de-duplicated.
 */
public record SendMoneyEvent(
    UUID eventId,
    EventType eventType,
    int schemaVersion,
    Instant occurredAt,
    UUID txnId,
    long senderWalletId,
    long receiverWalletId,
    long amount,
    long fee,
    long vat,
    long commission,
    long feeIncome,
    String currency,
    OptionalLong ledgerTimestamp,
    Optional<FailureCode> failureCode) {
}
