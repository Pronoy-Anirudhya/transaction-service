package com.bracits.transactionservice.adapter.out.jdbc.sql;

/**
 * SQL of {@code JdbcRuleRepository}. Both tables are tiny and read only by the rule cache.
 */
public final class RuleSql {

  public static final String FIND_ACTIVE_FEE_RULES = """
      SELECT rule_id, product, kyc_tier, min_amount, max_amount, fee_type, fee_value,
             fee_min, fee_max, vat_bps, commission_bps, active
        FROM fee_rule
       WHERE active
       ORDER BY product, kyc_tier, min_amount
      """;

  public static final String FIND_LIMIT_RULES = """
      SELECT product, kyc_tier, per_txn_min, per_txn_max, daily_amount, daily_count, monthly_amount, monthly_count
        FROM limit_rule
       ORDER BY product, kyc_tier
      """;

  private RuleSql() {
  }
}
