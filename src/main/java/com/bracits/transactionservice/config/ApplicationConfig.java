package com.bracits.transactionservice.config;

import com.bracits.transactionservice.config.mapper.SystemAccountsMapper;
import com.bracits.transactionservice.config.properties.LedgerProperties;
import com.bracits.transactionservice.domain.fee.calculator.FeeCalculator;
import com.bracits.transactionservice.domain.fee.calculator.impl.SlabFeeCalculator;
import com.bracits.transactionservice.domain.ledger.planner.LegPlanner;
import com.bracits.transactionservice.domain.ledger.planner.impl.LegPlannerImpl;
import com.bracits.transactionservice.domain.limit.policy.LimitPolicy;
import com.bracits.transactionservice.domain.limit.policy.impl.LimitPolicyImpl;
import com.bracits.transactionservice.domain.rules.chain.SendMoneyRuleChain;
import com.bracits.transactionservice.domain.rules.chain.impl.SendMoneyRuleChainImpl;
import com.bracits.transactionservice.port.out.repository.RuleRepository;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wires the framework-free domain services. Rules come from the cached {@link RuleRepository}
 * (P8).
 */
@Configuration
@EnableScheduling
public class ApplicationConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  SendMoneyRuleChain sendMoneyRuleChain() {
    return SendMoneyRuleChainImpl.standard();
  }

  @Bean
  FeeCalculator feeCalculator(RuleRepository rules) {
    return new SlabFeeCalculator(rules::findActiveFeeRules);
  }

  @Bean
  LimitPolicy limitPolicy(RuleRepository rules) {
    return new LimitPolicyImpl(rules::findLimitRules);
  }

  @Bean
  LegPlanner legPlanner(LedgerProperties ledger, SystemAccountsMapper mapper) {
    return new LegPlannerImpl(mapper.toSystemAccounts(ledger.accounts()));
  }
}
