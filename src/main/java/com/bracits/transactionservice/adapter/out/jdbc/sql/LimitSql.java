package com.bracits.transactionservice.adapter.out.jdbc.sql;

/** SQL of {@code JdbcLimitRepository}. */
public final class LimitSql {

  /**
   * Limit reservation, spec 6.1 verbatim: one statement, one row lock per sender. Counters of a past day / month are
   * reset in place; 0 rows updated means a daily or monthly amount or count limit would be exceeded.
   */
  public static final String RESERVE = """
      UPDATE wallet_limit_usage u SET
        day_amount   = CASE WHEN u.day   = :today THEN u.day_amount   ELSE 0 END + :amt,
        day_count    = CASE WHEN u.day   = :today THEN u.day_count    ELSE 0 END + 1,
        month_amount = CASE WHEN u.month = :month THEN u.month_amount ELSE 0 END + :amt,
        month_count  = CASE WHEN u.month = :month THEN u.month_count  ELSE 0 END + 1,
        day = :today, month = :month
      WHERE u.wallet_id = :sender
        AND CASE WHEN u.day   = :today THEN u.day_amount   ELSE 0 END + :amt <= :dailyAmount
        AND CASE WHEN u.day   = :today THEN u.day_count    ELSE 0 END + 1    <= :dailyCount
        AND CASE WHEN u.month = :month THEN u.month_amount ELSE 0 END + :amt <= :monthlyAmount
        AND CASE WHEN u.month = :month THEN u.month_count  ELSE 0 END + 1    <= :monthlyCount
      """;

  private LimitSql() {
  }
}
