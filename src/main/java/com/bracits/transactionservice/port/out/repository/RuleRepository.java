package com.bracits.transactionservice.port.out.repository;

import com.bracits.transactionservice.domain.fee.model.FeeRule;
import com.bracits.transactionservice.domain.limit.model.LimitRule;
import java.util.List;

/**
 * Fee and limit rules. The primary bean is the Caffeine-cached adapter (refresh 60 s, P8).
 */
public interface RuleRepository {

  /**
   * All {@code fee_rule} rows with {@code active = true}.
   */
  List<FeeRule> findActiveFeeRules();

  /**
   * All {@code limit_rule} rows.
   */
  List<LimitRule> findLimitRules();
}
