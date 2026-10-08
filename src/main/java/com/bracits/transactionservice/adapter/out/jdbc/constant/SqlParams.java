package com.bracits.transactionservice.adapter.out.jdbc.constant;

/**
 * Named-parameter names used in the SQL text blocks ({@code :name}) and by the parameter mappers.
 */
public final class SqlParams {

  // wallet
  public static final String WALLET_ID = "walletId";
  public static final String MSISDN = "msisdn";
  public static final String HOLDER_NAME = "holderName";
  public static final String WALLET_TYPE = "walletType";
  public static final String STATUS = "status";
  public static final String KYC_TIER = "kycTier";
  public static final String LEDGER_ACCOUNT_ID = "ledgerAccountId";
  public static final String BUSINESS_DATE = "businessDate";
  public static final String MONTH = "month";

  // limit reservation (spec 6.1)
  public static final String TODAY = "today";
  public static final String AMT = "amt";
  public static final String SENDER = "sender";
  public static final String DAILY_AMOUNT = "dailyAmount";
  public static final String DAILY_COUNT = "dailyCount";
  public static final String MONTHLY_AMOUNT = "monthlyAmount";
  public static final String MONTHLY_COUNT = "monthlyCount";

  // send_money_txn
  public static final String TXN_ID = "txnId";
  public static final String TXN_IDS = "txnIds";
  public static final String CLIENT_REF = "clientRef";
  public static final String REQUEST_HASH = "requestHash";
  public static final String SENDER_WALLET_ID = "senderWalletId";
  public static final String RECEIVER_WALLET_ID = "receiverWalletId";
  public static final String AMOUNT = "amount";
  public static final String FEE = "fee";
  public static final String VAT = "vat";
  public static final String COMMISSION = "commission";
  public static final String FEE_INCOME = "feeIncome";
  public static final String CURRENCY = "currency";
  public static final String REFERENCE = "reference";
  public static final String CODE = "code";
  public static final String LEDGER_TS = "ledgerTs";
  public static final String DELAY_MS = "delayMs";
  public static final String MIN_AGE_MS = "minAgeMs";
  public static final String LEASE_MS = "leaseMs";
  public static final String LIMIT = "limit";
  public static final String FROM_TXN_ID = "fromTxnId";
  public static final String TO_TXN_ID = "toTxnId";

  /**
   * PostgreSQL element type name of the {@code uuid[]} array bound to {@link #TXN_IDS}.
   */
  public static final String UUID_TYPE_NAME = "uuid";

  private SqlParams() {
  }
}
