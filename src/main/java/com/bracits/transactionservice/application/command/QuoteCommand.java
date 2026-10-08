package com.bracits.transactionservice.application.command;

/** Quote use-case input (FR-01). */
public record QuoteCommand(String senderMsisdn, String receiverMsisdn, long amount, String currency) {
}
