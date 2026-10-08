package com.bracits.transactionservice.domain.rules.chain.impl;

import com.bracits.transactionservice.domain.enums.FailureCode;
import com.bracits.transactionservice.domain.rules.chain.SendMoneyRuleChain;
import com.bracits.transactionservice.domain.rules.model.SendMoneyContext;
import com.bracits.transactionservice.domain.rules.rule.SendMoneyRule;
import com.bracits.transactionservice.domain.rules.rule.impl.AmountRangeRule;
import com.bracits.transactionservice.domain.rules.rule.impl.CustomerTypeRule;
import com.bracits.transactionservice.domain.rules.rule.impl.SelfTransferRule;
import com.bracits.transactionservice.domain.rules.rule.impl.WalletActiveRule;
import com.bracits.transactionservice.domain.rules.rule.impl.WalletExistsRule;
import java.util.List;
import java.util.Optional;

/**
 * Default implementation of {@link SendMoneyRuleChain}.
 */
public final class SendMoneyRuleChainImpl implements SendMoneyRuleChain {

  private final List<SendMoneyRule> rules;

  public SendMoneyRuleChainImpl(List<SendMoneyRule> rules) {
    this.rules = List.copyOf(rules);
  }

  /**
   * The standard Send Money chain (BR-01, BR-02), cheapest and most fundamental checks first.
   */
  public static SendMoneyRuleChain standard() {
    return new SendMoneyRuleChainImpl(List.of(
        new SelfTransferRule(),
        new WalletExistsRule(),
        new WalletActiveRule(),
        new CustomerTypeRule(),
        new AmountRangeRule()));
  }

  /**
   * The first failing rule's code, or empty when every rule passes.
   */
  @Override
  public Optional<FailureCode> evaluate(SendMoneyContext ctx) {
    for (SendMoneyRule rule : rules) {
      Optional<FailureCode> failure = rule.check(ctx);
      if (failure.isPresent()) {
        return failure;
      }
    }

    return Optional.empty();
  }

  /**
   * The rules in evaluation order.
   */
  @Override
  public List<SendMoneyRule> rules() {
    return rules;
  }
}
