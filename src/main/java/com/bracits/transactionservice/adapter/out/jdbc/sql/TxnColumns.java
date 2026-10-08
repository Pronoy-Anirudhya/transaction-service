package com.bracits.transactionservice.adapter.out.jdbc.sql;

/** Column names of {@code send_money_txn}. */
public final class TxnColumns {

  public static final String TXN_ID = "txn_id";
  public static final String CLIENT_REF = "client_ref";
  public static final String REQUEST_HASH = "request_hash";
  public static final String SENDER_WALLET_ID = "sender_wallet_id";
  public static final String RECEIVER_WALLET_ID = "receiver_wallet_id";
  public static final String AMOUNT = "amount";
  public static final String FEE = "fee";
  public static final String VAT = "vat";
  public static final String COMMISSION = "commission";
  public static final String FEE_INCOME = "fee_income";
  public static final String CURRENCY = "currency";
  public static final String REFERENCE = "reference";
  public static final String BUSINESS_DATE = "business_date";
  public static final String STATUS = "status";
  public static final String FAILURE_CODE = "failure_code";
  public static final String LEDGER_ATTEMPTS = "ledger_attempts";
  public static final String NEXT_CHECK_AT = "next_check_at";
  public static final String LEDGER_TS = "ledger_ts";
  public static final String CREATED_AT = "created_at";
  public static final String COMPLETED_AT = "completed_at";
  public static final String EVENT_PUBLISHED_AT = "event_published_at";

  private TxnColumns() {
  }
}
