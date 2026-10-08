package com.bracits.transactionservice.application.command;

/**
 * Test-profile funding input; {@code idempotencyKey} makes the funding ID deterministic.
 */
public record FundWalletCommand(String msisdn, String idempotencyKey, long amount) {

}
