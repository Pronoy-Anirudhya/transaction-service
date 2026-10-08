package com.bracits.transactionservice.port.out;

import com.bracits.transactionservice.domain.fee.FeeRule;
import com.bracits.transactionservice.domain.limit.LimitRule;

import java.util.List;

/** Fee and limit rules. The primary bean is the Caffeine-cached adapter (refresh 60 s, P8). */
public interface RuleRepository {

  /** All {@code fee_rule} rows with {@code active = true}. */
  List<FeeRule> findActiveFeeRules();

  /** All {@code limit_rule} rows. */
  List<LimitRule> findLimitRules();
}
