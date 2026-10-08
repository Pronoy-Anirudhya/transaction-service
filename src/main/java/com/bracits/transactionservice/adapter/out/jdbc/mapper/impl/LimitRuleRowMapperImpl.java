package com.bracits.transactionservice.adapter.out.jdbc.mapper.impl;

import com.bracits.transactionservice.adapter.out.jdbc.constant.RuleColumns;
import com.bracits.transactionservice.adapter.out.jdbc.mapper.LimitRuleRowMapper;
import com.bracits.transactionservice.domain.enums.Product;
import com.bracits.transactionservice.domain.limit.model.LimitRule;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link LimitRuleRowMapper}.
 */
@Component
public final class LimitRuleRowMapperImpl implements LimitRuleRowMapper {

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
