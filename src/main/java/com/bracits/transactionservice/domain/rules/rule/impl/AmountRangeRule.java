package com.bracits.transactionservice.domain.rules.rule.impl;

import com.bracits.transactionservice.domain.enums.FailureCode;
import com.bracits.transactionservice.domain.rules.model.SendMoneyContext;
import com.bracits.transactionservice.domain.rules.rule.SendMoneyRule;
import java.util.Optional;

/**
 * BR-02: the amount is within the sender tier's per-transaction range. No limit rule for the tier
 * fails too.
 */
public final class AmountRangeRule implements SendMoneyRule {

  @Override
  public Optional<FailureCode> check(SendMoneyContext ctx) {
    boolean allowed = ctx.senderLimitRule()
        .map(rule -> rule.allowsAmount(ctx.amount()))
        .orElse(false);
    return allowed ? Optional.empty() : Optional.of(FailureCode.AMOUNT_OUT_OF_RANGE);
  }
}
