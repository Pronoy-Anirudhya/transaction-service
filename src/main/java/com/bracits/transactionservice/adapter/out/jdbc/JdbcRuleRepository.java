package com.bracits.transactionservice.adapter.out.jdbc;

import com.bracits.transactionservice.adapter.out.jdbc.mapper.FeeRuleRowMapper;
import com.bracits.transactionservice.adapter.out.jdbc.mapper.LimitRuleRowMapper;
import com.bracits.transactionservice.adapter.out.jdbc.sql.RuleSql;
import com.bracits.transactionservice.domain.fee.FeeRule;
import com.bracits.transactionservice.domain.limit.LimitRule;
import com.bracits.transactionservice.port.out.RuleRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;

/** {@code fee_rule} and {@code limit_rule} over {@link JdbcClient}. Read only by the Caffeine rule cache. */
@Component
public final class JdbcRuleRepository implements RuleRepository {

  private final JdbcClient jdbc;
  private final FeeRuleRowMapper feeRuleMapper;
  private final LimitRuleRowMapper limitRuleMapper;

  public JdbcRuleRepository(JdbcClient jdbc, FeeRuleRowMapper feeRuleMapper, LimitRuleRowMapper limitRuleMapper) {
    this.jdbc = jdbc;
    this.feeRuleMapper = feeRuleMapper;
    this.limitRuleMapper = limitRuleMapper;
  }

  @Override
  public List<FeeRule> findActiveFeeRules() {
    return List.copyOf(jdbc.sql(RuleSql.FIND_ACTIVE_FEE_RULES).query(feeRuleMapper).list());
  }

  @Override
  public List<LimitRule> findLimitRules() {
    return List.copyOf(jdbc.sql(RuleSql.FIND_LIMIT_RULES).query(limitRuleMapper).list());
  }
}
