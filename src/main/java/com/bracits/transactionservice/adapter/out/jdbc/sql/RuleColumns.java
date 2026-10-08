package com.bracits.transactionservice.adapter.out.jdbc.sql;

/** Column names of {@code fee_rule} and {@code limit_rule}. */
public final class RuleColumns {

  // shared
  public static final String PRODUCT = "product";
  public static final String KYC_TIER = "kyc_tier";

  // fee_rule
  public static final String RULE_ID = "rule_id";
  public static final String MIN_AMOUNT = "min_amount";
  public static final String MAX_AMOUNT = "max_amount";
  public static final String FEE_TYPE = "fee_type";
  public static final String FEE_VALUE = "fee_value";
  public static final String FEE_MIN = "fee_min";
  public static final String FEE_MAX = "fee_max";
  public static final String VAT_BPS = "vat_bps";
  public static final String COMMISSION_BPS = "commission_bps";
  public static final String ACTIVE = "active";

  // limit_rule
  public static final String PER_TXN_MIN = "per_txn_min";
  public static final String PER_TXN_MAX = "per_txn_max";
  public static final String DAILY_AMOUNT = "daily_amount";
  public static final String DAILY_COUNT = "daily_count";
  public static final String MONTHLY_AMOUNT = "monthly_amount";
  public static final String MONTHLY_COUNT = "monthly_count";

  private RuleColumns() {
  }
}
