package com.bracits.transactionservice.adapter.out.jdbc.mapper;

import com.bracits.transactionservice.adapter.out.jdbc.sql.RuleColumns;
import com.bracits.transactionservice.domain.Product;
import com.bracits.transactionservice.domain.limit.LimitRule;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

/** {@code limit_rule} row → {@link LimitRule}. */
@Component
public final class LimitRuleRowMapper implements RowMapper<LimitRule> {

  @Override
  public LimitRule mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new LimitRule(
        Product.valueOf(rs.getString(RuleColumns.PRODUCT)),
        rs.getInt(RuleColumns.KYC_TIER),
        rs.getLong(RuleColumns.PER_TXN_MIN),
        rs.getLong(RuleColumns.PER_TXN_MAX),
        rs.getLong(RuleColumns.DAILY_AMOUNT),
        rs.getInt(RuleColumns.DAILY_COUNT),
        rs.getLong(RuleColumns.MONTHLY_AMOUNT),
        rs.getInt(RuleColumns.MONTHLY_COUNT));
  }
}
