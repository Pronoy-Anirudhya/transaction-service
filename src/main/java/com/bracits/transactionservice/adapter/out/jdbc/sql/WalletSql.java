package com.bracits.transactionservice.adapter.out.jdbc.sql;

/** SQL of {@code JdbcWalletRepository}. One statement per repository method. */
public final class WalletSql {

  public static final String FIND_BY_MSISDN = """
      SELECT wallet_id, msisdn, holder_name, wallet_type, status, kyc_tier, ledger_account_id
        FROM wallet
       WHERE msisdn = :msisdn
      """;

  public static final String FIND_BY_ID = """
      SELECT wallet_id, msisdn, holder_name, wallet_type, status, kyc_tier, ledger_account_id
        FROM wallet
       WHERE wallet_id = :walletId
      """;

  /**
   * Wallet and its {@code wallet_limit_usage} row in one statement. On a duplicate MSISDN {@code w} is empty, so
   * nothing is written and no row is returned. The FK of the usage row is checked at the end of the statement.
   */
  public static final String INSERT_IF_ABSENT = """
      WITH w AS (
        INSERT INTO wallet (msisdn, holder_name, wallet_type, status, kyc_tier, ledger_account_id)
        VALUES (:msisdn, :holderName, :walletType, :status, :kycTier, :ledgerAccountId)
        ON CONFLICT (msisdn) DO NOTHING
        RETURNING wallet_id, msisdn, holder_name, wallet_type, status, kyc_tier, ledger_account_id
      ), limit_usage AS (
        INSERT INTO wallet_limit_usage (wallet_id, day, month)
        SELECT w.wallet_id, :businessDate, :month FROM w
      )
      SELECT wallet_id, msisdn, holder_name, wallet_type, status, kyc_tier, ledger_account_id FROM w
      """;

  private WalletSql() {
  }
}
