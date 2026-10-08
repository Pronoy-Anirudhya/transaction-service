package com.bracits.transactionservice.config;

import com.bracits.transactionservice.domain.fee.FeeCalculator;
import com.bracits.transactionservice.domain.fee.SlabFeeCalculator;
import com.bracits.transactionservice.domain.ledger.LegPlanner;
import com.bracits.transactionservice.domain.limit.LimitPolicy;
import com.bracits.transactionservice.domain.rules.SendMoneyRuleChain;
import com.bracits.transactionservice.port.out.RuleRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/** Wires the framework-free domain services. Rules come from the cached {@link RuleRepository} (P8). */
@Configuration
@EnableScheduling
public class ApplicationConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  SendMoneyRuleChain sendMoneyRuleChain() {
    return SendMoneyRuleChain.standard();
  }

  @Bean
  FeeCalculator feeCalculator(RuleRepository rules) {
    return new SlabFeeCalculator(rules::findActiveFeeRules);
  }

  @Bean
  LimitPolicy limitPolicy(RuleRepository rules) {
    return new LimitPolicy(rules::findLimitRules);
  }

  @Bean
  LegPlanner legPlanner(LedgerProperties ledger, SystemAccountsMapper mapper) {
    return new LegPlanner(mapper.toSystemAccounts(ledger.accounts()));
  }
}
