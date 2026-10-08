package com.bracits.transactionservice.domain.rules.chain;

import com.bracits.transactionservice.domain.enums.FailureCode;
import com.bracits.transactionservice.domain.rules.model.SendMoneyContext;
import com.bracits.transactionservice.domain.rules.rule.SendMoneyRule;
import java.util.List;
import java.util.Optional;

/**
 * Chain of Responsibility over an ordered list of {@link SendMoneyRule}s. The first failure wins.
 */
public interface SendMoneyRuleChain {

  /**
   * The first failing rule's code, or empty when every rule passes.
   */
  Optional<FailureCode> evaluate(SendMoneyContext ctx);

  /**
   * The rules in evaluation order.
   */
  List<SendMoneyRule> rules();
}
