package com.bracits.transactionservice.application.command;

import java.util.Optional;

/** Send Money use-case input. {@code idempotencyKey} is the {@code Idempotency-Key} header (≤ 64 chars). */
public record SendMoneyCommand(
    String idempotencyKey,
    String senderMsisdn,
    String receiverMsisdn,
    long amount,
    String currency,
    Optional<String> reference,
    Optional<String> quoteToken) {
}
