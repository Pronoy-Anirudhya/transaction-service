package com.bracits.transactionservice.domain.rules;

import com.bracits.transactionservice.domain.FailureCode;

import java.util.List;
import java.util.Optional;

/** Chain of Responsibility over an ordered list of {@link SendMoneyRule}s. The first failure wins. */
public final class SendMoneyRuleChain {

  private final List<SendMoneyRule> rules;

  public SendMoneyRuleChain(List<SendMoneyRule> rules) {
    this.rules = List.copyOf(rules);
  }

  /** The standard Send Money chain (BR-01, BR-02), cheapest and most fundamental checks first. */
  public static SendMoneyRuleChain standard() {
    return new SendMoneyRuleChain(List.of(
        new SelfTransferRule(),
        new WalletExistsRule(),
        new WalletActiveRule(),
        new CustomerTypeRule(),
        new AmountRangeRule()));
  }

  /** The first failing rule's code, or empty when every rule passes. */
  public Optional<FailureCode> evaluate(SendMoneyContext ctx) {
    for (SendMoneyRule rule : rules) {
      Optional<FailureCode> failure = rule.check(ctx);
      if (failure.isPresent()) {
        return failure;
      }
    }
    return Optional.empty();
  }

  /** The rules in evaluation order. */
  public List<SendMoneyRule> rules() {
    return rules;
  }
}
