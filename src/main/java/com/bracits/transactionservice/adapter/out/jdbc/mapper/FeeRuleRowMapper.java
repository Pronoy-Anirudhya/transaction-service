package com.bracits.transactionservice.adapter.out.jdbc.mapper;

import com.bracits.transactionservice.adapter.out.jdbc.sql.RuleColumns;
import com.bracits.transactionservice.domain.Product;
import com.bracits.transactionservice.domain.fee.FeeRule;
import com.bracits.transactionservice.domain.fee.FeeType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

/** {@code fee_rule} row → {@link FeeRule}; {@code fee_max} NULL → empty. */
@Component
public final class FeeRuleRowMapper implements RowMapper<FeeRule> {

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
