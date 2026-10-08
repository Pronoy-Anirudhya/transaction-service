package com.bracits.transactionservice.domain.rules;

import com.bracits.transactionservice.domain.FailureCode;

import java.util.Optional;

/** BR-01: the sender and the receiver must differ. */
public final class SelfTransferRule implements SendMoneyRule {

  @Override
  public Optional<FailureCode> check(SendMoneyContext ctx) {
    return ctx.senderMsisdn().equals(ctx.receiverMsisdn())
        ? Optional.of(FailureCode.SELF_TRANSFER)
        : Optional.empty();
  }
}
