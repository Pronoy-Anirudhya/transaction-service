package com.bracits.transactionservice.adapter.out.jdbc.mapper.impl;

import com.bracits.transactionservice.adapter.out.jdbc.constant.RuleColumns;
import com.bracits.transactionservice.adapter.out.jdbc.mapper.FeeRuleRowMapper;
import com.bracits.transactionservice.adapter.out.jdbc.util.ResultSetValues;
import com.bracits.transactionservice.domain.enums.Product;
import com.bracits.transactionservice.domain.fee.enums.FeeType;
import com.bracits.transactionservice.domain.fee.model.FeeRule;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link FeeRuleRowMapper}.
 */
@Component
public final class FeeRuleRowMapperImpl implements FeeRuleRowMapper {

  @Override
  public FeeRule mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new FeeRule(
        rs.getLong(RuleColumns.RULE_ID),
        Product.valueOf(rs.getString(RuleColumns.PRODUCT)),
        rs.getInt(RuleColumns.KYC_TIER),
        rs.getLong(RuleColumns.MIN_AMOUNT),
        rs.getLong(RuleColumns.MAX_AMOUNT),
        FeeType.valueOf(rs.getString(RuleColumns.FEE_TYPE)),
        rs.getLong(RuleColumns.FEE_VALUE),
        rs.getLong(RuleColumns.FEE_MIN),
        ResultSetValues.optionalLong(rs, RuleColumns.FEE_MAX),
        rs.getInt(RuleColumns.VAT_BPS),
        rs.getInt(RuleColumns.COMMISSION_BPS),
        rs.getBoolean(RuleColumns.ACTIVE));
  }
}
