package com.bracits.transactionservice.domain.rules.rule;

import com.bracits.transactionservice.domain.enums.FailureCode;
import com.bracits.transactionservice.domain.rules.model.SendMoneyContext;
import java.util.Optional;

/**
 * Chain of Responsibility link: one business rule. Returns the failure code if the rule fails,
 * empty if it passes. Rules run in order; the first failure wins. A new rule is a new class.
 */
public interface SendMoneyRule {

  Optional<FailureCode> check(SendMoneyContext ctx);
}
