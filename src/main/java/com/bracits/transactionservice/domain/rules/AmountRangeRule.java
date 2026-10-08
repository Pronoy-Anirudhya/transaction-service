package com.bracits.transactionservice.domain.rules;

import com.bracits.transactionservice.domain.FailureCode;

import java.util.Optional;

/** BR-02: the amount is within the sender tier's per-transaction range. No limit rule for the tier fails too. */
public final class AmountRangeRule implements SendMoneyRule {

  @Override
  public Optional<FailureCode> check(SendMoneyContext ctx) {
    boolean allowed = ctx.senderLimitRule()
        .map(rule -> rule.allowsAmount(ctx.amount()))
        .orElse(false);
    return allowed ? Optional.empty() : Optional.of(FailureCode.AMOUNT_OUT_OF_RANGE);
  }
}
