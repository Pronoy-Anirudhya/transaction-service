package com.bracits.transactionservice.application.quote;

import java.time.Instant;

/** What a quote token vouches for. */
public record QuoteClaims(String senderMsisdn, String receiverMsisdn, long amount, long fee, Instant expiresAt) {

  /** True if the token is for this exact sender, receiver and amount, and has not expired at {@code now}. */
  public boolean matches(String sender, String receiver, long candidateAmount, Instant now) {
    return senderMsisdn.equals(sender)
        && receiverMsisdn.equals(receiver)
        && amount == candidateAmount
        && now.isBefore(expiresAt);
  }
}
